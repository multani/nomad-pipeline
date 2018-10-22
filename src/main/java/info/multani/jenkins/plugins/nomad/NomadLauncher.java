/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package info.multani.jenkins.plugins.nomad;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.hashicorp.nomad.apimodel.AllocationListStub;
import com.hashicorp.nomad.apimodel.Job;
import com.hashicorp.nomad.apimodel.TaskState;
import com.hashicorp.nomad.javasdk.ErrorResponseException;
import com.hashicorp.nomad.javasdk.EvaluationResponse;
import com.hashicorp.nomad.javasdk.NomadApiClient;
import com.hashicorp.nomad.javasdk.ServerQueryResponse;
import hudson.AbortException;
import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import static java.util.logging.Level.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Launches on Nomad the specified {@link NomadComputer} instance.
 */
public class NomadLauncher extends JNLPLauncher {

    private static final Logger LOGGER = Logger.getLogger(NomadLauncher.class.getName());

    private boolean launched;

    @DataBoundConstructor
    public NomadLauncher(String tunnel, String vmargs) {
        super(tunnel, vmargs);
    }

    public NomadLauncher() {
        super();
    }

    @Override
    public boolean isLaunchSupported() {
        return !launched;
    }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) {
        PrintStream logger = listener.getLogger();

        if (!(computer instanceof NomadComputer)) {
            throw new IllegalArgumentException("This Launcher can be used only with NomadComputer");
        }
        NomadComputer nomadComputer = (NomadComputer) computer;
        computer.setAcceptingTasks(false);
        NomadSlave slave = nomadComputer.getNode();
        if (slave == null) {
            throw new IllegalStateException("Node has been removed, cannot launch " + computer.getName());
        }
        if (launched) {
            LOGGER.log(INFO, "Agent has already been launched, activating: {0}",
                    slave.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        NomadCloud cloud = slave.getNomadCloud();
        final NomadJobTemplate unwrappedTemplate = slave.getTemplate();
        try {
            NomadApiClient client = cloud.connect();
            Job job = getJobTemplate(slave, unwrappedTemplate);
            String jobID = job.getId();

            LOGGER.log(Level.FINE, "Creating Nomad job: {0}", jobID);

            EvaluationResponse evaluation;
            try {
                evaluation = client.getJobsApi().register(job);
            } catch (ErrorResponseException exc) {
                String msg = String.format("Unable to evaluate Nomad job '%s': %s", jobID, exc.getServerErrorMessage());
                LOGGER.log(Level.SEVERE, msg, exc);
                throw new AbortException(msg); // TODO: we should probably abort the build here, but AbortException doesn't do it.
            }

            String evaluationID = evaluation.getValue();
            LOGGER.log(INFO, "Registered Nomad job {0} with evaluation ID: {1}",
                    new Object[]{jobID, evaluationID});
            LOGGER.log(FINE, "Created Nomad job: {0}", jobID);

            logger.printf("[Nomad] Registered Nomad job %s with evaluation ID %s%n",
                    jobID, evaluationID);

            // We need the job to be running and connected before returning
            // otherwise this method keeps being called multiple times
            List<String> validStates = ImmutableList.of("running");

            int i = 0;
            int j = 100; // wait 600 seconds

            // TODO: wait for Job to be running
//            List<ContainerStatus> containerStatuses = null;
            String jobStatus = "<unknown>"; // keep the compiler happy
            // wait for Job to be running
            for (; i < j; i++) {
                LOGGER.log(INFO, "Waiting for job to be scheduled ({1}/{2}): {0}", new Object[]{jobID, i, j});
                logger.printf("Waiting for job to be scheduled (%2$s/%3$s): %1$s%n", jobID, i, j);

                Thread.sleep(6000);
                // TODO catch com.hashicorp.nomad.javasdk.ErrorResponseException 404
                ServerQueryResponse<Job> response = client.getJobsApi().info(jobID);

                if (response == null) { // can exist?
                    throw new IllegalStateException("Job no longer exists: " + jobID);
                }

                job = response.getValue();
                jobStatus = job.getStatus();

                LOGGER.log(INFO, "Nomad job {0} is: {1}", new Object[]{jobID, jobStatus});
                logger.printf("Nomad job %1$s is: %2$s%n", jobID, jobStatus);

                ServerQueryResponse<List<AllocationListStub>> r = client.getJobsApi().allocations(jobID);

                class AllocationComparator implements Comparator<AllocationListStub> {

                    @Override
                    public int compare(AllocationListStub a, AllocationListStub b) {
                        // Sort by greater CreateIndex first. This should be the
                        // last allocation created for this Nomad job.
                        return b.getCreateIndex().compareTo(a.getCreateIndex());
                    }
                }

                // TODO: if the lastAlloc ClientStatus is "failed" already, we can probably shutdown the check earlier.
                AllocationListStub lastAlloc = r.getValue().stream()
                        .sorted(new AllocationComparator())
                        .findFirst()
                        .get();

                LOGGER.log(FINE, "Checking status of allocation {0} for Nomad job {1} (status={2})",
                        new Object[]{lastAlloc.getId(), jobID, lastAlloc.getClientStatus()});
                logger.printf("Checking status of allocation %1$s for Nomad job %2$s (status=%3$s)%n",
                        lastAlloc.getId(), jobID, lastAlloc.getClientStatus());

                List<Map.Entry<String, TaskState>> terminatedTasks = new ArrayList<>();
                Boolean allContainersAreReady = true;
                for (Map.Entry<String, TaskState> entry : lastAlloc.getTaskStates().entrySet()) {
                    String taskName = entry.getKey();
                    TaskState taskState = entry.getValue();

                    if (!taskState.getState().equals("running")) {
                        // Task is waiting for some reason
                        LOGGER.log(INFO, "Task is not running {0} [{1}]: {2} (failed={3})",
                                new Object[]{jobID, taskName, taskState.getState(), taskState.getFailed()});
                        logger.printf("Task is not running %1$s [%2$s]: %3$s (failed=%4$s)%n",
                                jobID, taskName, taskState.getState(), taskState.getFailed());
                        // break;
                    }
                    if (taskState.getState().equals("dead") && taskState.getFailed()) {
                        terminatedTasks.add(entry);
                    } else if (!taskState.getState().equals("running")) {
                        allContainersAreReady = false;
                    }
                }

                if (!terminatedTasks.isEmpty()) {
                    List<String> tasks = terminatedTasks.stream()
                            .map(entry -> entry.getKey())
                            .collect(Collectors.toList());

                    throw new IllegalStateException("Tasks have failed: " + tasks);
                }

                if (!allContainersAreReady) {
                    continue;
                }

                if (!jobStatus.equals("pending")) {
                    break;
                }
            }

            if (!validStates.contains(jobStatus)) {
                throw new IllegalStateException("Nomad job " + jobID + " is not running after " + j + " attempts, status: " + jobStatus);
            }

            j = unwrappedTemplate.getSlaveConnectTimeout();

            // now wait for agent to be online
            for (; i < j; i++) {
                if (slave.getComputer() == null) {
                    throw new IllegalStateException("Node was deleted, computer is null");
                }
                if (slave.getComputer().isOnline()) {
                    break;
                }
                LOGGER.log(INFO, "Waiting for agent to connect ({1}/{2}): {0}", new Object[]{jobID, i, j});
                logger.printf("Waiting for agent to connect (%2$s/%3$s): %1$s%n", jobID, i, j);
                Thread.sleep(1000);
            }
            if (!slave.getComputer().isOnline()) {
                throw new IllegalStateException("Agent is not connected after " + j + " attempts, status: " + jobStatus);
            }
            computer.setAcceptingTasks(true);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, String.format("Error in provisioning; agent=%s, template=%s", slave, unwrappedTemplate), ex);
            LOGGER.log(Level.FINER, "Removing Jenkins node: {0}", slave.getNodeName());
            try {
                slave.terminate();
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Unable to remove Jenkins node", e);
            }
            throw Throwables.propagate(ex);
        }
        launched = true;
        try {
            // We need to persist the "launched" setting...
            slave.save();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not save() agent: " + e.getMessage(), e);
        } catch (Exception ex) {
            Logger.getLogger(NomadLauncher.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private Job getJobTemplate(NomadSlave slave, NomadJobTemplate template) {
        return template == null ? null : template.build(slave);
    }
}
