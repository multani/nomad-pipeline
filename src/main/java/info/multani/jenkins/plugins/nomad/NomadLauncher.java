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

import static java.util.logging.Level.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.stapler.DataBoundConstructor;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.hashicorp.nomad.apimodel.Job;
import com.hashicorp.nomad.javasdk.NomadApiClient;

import hudson.model.TaskListener;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;

/**
 * Launches on Kubernetes the specified {@link NomadComputer} instance.
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
            throw new IllegalArgumentException("This Launcher can be used only with KubernetesComputer");
        }
        NomadComputer kubernetesComputer = (NomadComputer) computer;
        computer.setAcceptingTasks(false);
        NomadSlave slave = kubernetesComputer.getNode();
        if (slave == null) {
            throw new IllegalStateException("Node has been removed, cannot launch " + computer.getName());
        }
        if (launched) {
            LOGGER.log(INFO, "Agent has already been launched, activating: {}", slave.getNodeName());
            computer.setAcceptingTasks(true);
            return;
        }

        NomadCloud cloud = slave.getNomadCloud();
        final NomadJobTemplate unwrappedTemplate = slave.getTemplate();
        try {
            NomadApiClient client = cloud.connect();
            Job job = getPodTemplate(slave, unwrappedTemplate);

            String jobID = job.getName();
            String namespace = ""; //StringUtils.defaultIfBlank(slave.getNamespace(), client.getNamespace());

            LOGGER.log(Level.FINE, "Creating Pod: {0} in namespace {1}", new Object[]{jobID, namespace});
            client.getJobsApi().register(job);
//                    .jobs().inNamespace(namespace).create(job);
            LOGGER.log(INFO, "Created Pod: {0} in namespace {1}", new Object[]{jobID, namespace});
            logger.printf("Created Pod: %s in namespace %s%n", jobID, namespace);

            // We need the pod to be running and connected before returning
            // otherwise this method keeps being called multiple times
            List<String> validStates = ImmutableList.of("Running");

            int i = 0;
            int j = 100; // wait 600 seconds

            launched = true;
            return;

            // TODO: wait for Pod to be running
//            List<ContainerStatus> containerStatuses = null;
//
//            // wait for Pod to be running
//            for (; i < j; i++) {
//                LOGGER.log(INFO, "Waiting for Pod to be scheduled ({1}/{2}): {0}", new Object[]{jobID, i, j});
//                logger.printf("Waiting for Pod to be scheduled (%2$s/%3$s): %1$s%n", jobID, i, j);
//
//                Thread.sleep(6000);
//                job = client.pods().inNamespace(namespace).withName(jobID).get();
//                if (job == null) {
//                    throw new IllegalStateException("Pod no longer exists: " + jobID);
//                }
//
//                containerStatuses = job.getStatus().getContainerStatuses();
//                List<ContainerStatus> terminatedContainers = new ArrayList<>();
//                Boolean allContainersAreReady = true;
//                for (ContainerStatus info : containerStatuses) {
//                    if (info != null) {
//                        if (info.getState().getWaiting() != null) {
//                            // Pod is waiting for some reason
//                            LOGGER.log(INFO, "Container is waiting {0} [{2}]: {1}",
//                                    new Object[]{jobID, info.getState().getWaiting(), info.getName()});
//                            logger.printf("Container is waiting %1$s [%3$s]: %2$s%n",
//                                    jobID, info.getState().getWaiting(), info.getName());
//                            // break;
//                        }
//                        if (info.getState().getTerminated() != null) {
//                            terminatedContainers.add(info);
//                        } else if (!info.getReady()) {
//                            allContainersAreReady = false;
//                        }
//                    }
//                }
//
//                if (!terminatedContainers.isEmpty()) {
//                    Map<String, Integer> errors = terminatedContainers.stream().collect(Collectors
//                            .toMap(ContainerStatus::getName, (info) -> info.getState().getTerminated().getExitCode()));
//
//                    // Print the last lines of failed containers
//                    logLastLines(terminatedContainers, jobID, namespace, slave, errors, client);
//                    throw new IllegalStateException("Containers are terminated with exit codes: " + errors);
//                }
//
//                if (!allContainersAreReady) {
//                    continue;
//                }
//
//                if (validStates.contains(job.getStatus().getPhase())) {
//                    break;
//                }
//            }
//            String status = job.getStatus().getPhase();
//            if (!validStates.contains(status)) {
//                throw new IllegalStateException("Container is not running after " + j + " attempts, status: " + status);
//            }
//
//            j = unwrappedTemplate.getSlaveConnectTimeout();
//
//            // now wait for agent to be online
//            for (; i < j; i++) {
//                if (slave.getComputer() == null) {
//                    throw new IllegalStateException("Node was deleted, computer is null");
//                }
//                if (slave.getComputer().isOnline()) {
//                    break;
//                }
//                LOGGER.log(INFO, "Waiting for agent to connect ({1}/{2}): {0}", new Object[]{jobID, i, j});
//                logger.printf("Waiting for agent to connect (%2$s/%3$s): %1$s%n", jobID, i, j);
//                Thread.sleep(1000);
//            }
//            if (!slave.getComputer().isOnline()) {
//                if (containerStatuses != null) {
//                    logLastLines(containerStatuses, jobID, namespace, slave, null, client);
//                }
//                throw new IllegalStateException("Agent is not connected after " + j + " attempts, status: " + status);
//            }
//            computer.setAcceptingTasks(true);
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
//        launched = true;
//        try {
//            // We need to persist the "launched" setting...
//            slave.save();
//        } catch (IOException e) {
//            LOGGER.log(Level.WARNING, "Could not save() agent: " + e.getMessage(), e);
//        }
    }

    private Job getPodTemplate(NomadSlave slave, NomadJobTemplate template) {
        return template == null ? null : template.build(slave);
    }

    /**
     * Log the last lines of containers logs
     */
//    private void logLastLines(List<ContainerStatus> containers, String podId, String namespace, KubernetesSlave slave,
//            Map<String, Integer> errors, NomadClient client) {
//        for (ContainerStatus containerStatus : containers) {
//            String containerName = containerStatus.getName();
//            PrettyLoggable<String, LogWatch> tailingLines = client.pods().inNamespace(namespace)
//                    .withName(podId).inContainer(containerStatus.getName()).tailingLines(30);
//            String log = tailingLines.getLog();
//            if (!StringUtils.isBlank(log)) {
//                String msg = errors != null ? String.format(" exited with error %s", errors.get(containerName))
//                        : "";
//                LOGGER.log(Level.SEVERE,
//                        "Error in provisioning; agent={0}, template={1}. Container {2}{3}. Logs: {4}",
//                        new Object[]{slave, slave.getTemplate(), containerName, msg, tailingLines.getLog()});
//            }
//        }
//    }

}
