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

import java.util.concurrent.Callable;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.Label;
import hudson.model.Node;

/**
 * Callback for Kubernetes cloud provision
 * 
 * @since 0.13
 */
class ProvisioningCallback implements Callable<Node> {

    @Nonnull
    private final NomadCloud cloud;
    @Nonnull
    private final NomadJobTemplate t;

    /**
     * @deprecated Use {@link ProvisioningCallback#ProvisioningCallback(KubernetesCloud, PodTemplate)} instead.
     */
    @Deprecated
    public ProvisioningCallback(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate t, @CheckForNull Label label) {
        this(cloud, t);
    }

    public ProvisioningCallback(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate t) {
        this.cloud = cloud;
        this.t = t;
    }

    public Node call() throws Exception {
        return NomadSlave
                .builder()
                    .podTemplate(t) //cloud.getUnwrappedTemplate(t))
                    .cloud(cloud)
                .build();
    }

}
