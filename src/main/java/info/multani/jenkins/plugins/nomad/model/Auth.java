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
package info.multani.jenkins.plugins.nomad.model;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import java.io.Serializable;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class Auth extends AbstractDescribableImpl<Auth>
        implements Serializable, ExtensionPoint {

    private static final long serialVersionUID = 5332584988703580876L;

    private String username;
    private String password;
    private String serverAddress;

    @DataBoundConstructor
    public Auth(String username, String password, String serverAddress) {
        this.username = username;
        this.password = password;
        this.serverAddress = serverAddress;
    }

    public String getUsername() {
        return (username == null) ? "" : username;
    }

    public void setUsername(String value) {
        this.username = value;
    }

    public String getPassword() {
        return (password == null) ? "" : password;
    }

    public void setPassword(String value) {
        this.password = value;
    }

    public String getServerAddress() {
        return (serverAddress == null) ? "" : serverAddress;
    }

    public void setServerAddress(String value) {
        this.serverAddress = value;
    }
    @Override
    public String toString() {
        return "Auth[username=" + getUsername() + ", password=" + getPassword() + ", serverAddress" + getServerAddress()  + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((username == null) ? 0 : username.hashCode());
        result = prime * result + ((password == null) ? 0 : password.hashCode());
        result = prime * result + ((serverAddress == null) ? 0 : serverAddress.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Auth)) {
            return false;
        }

        Auth other = (Auth) obj;

        if (username == null) {
            if (other.username != null) {
                return false;
            }
        } else if (!username.equals(other.username)) {
            return false;
        }

        if (password == null) {
            if (other.password != null) {
                return false;
            }
        } else if (!password.equals(other.password)) {
            return false;
        }

        if (serverAddress == null) {
            if (other.serverAddress != null) {
                return false;
            }
        } else if (!serverAddress.equals(other.serverAddress)) {
            return false;
        }

        return true;
    }

    @Extension
    @Symbol("auth")
    public static class DescriptorImpl extends Descriptor<Auth> {

        @Override
        public String getDisplayName() {
            return "Auth";
        }
    }
}
