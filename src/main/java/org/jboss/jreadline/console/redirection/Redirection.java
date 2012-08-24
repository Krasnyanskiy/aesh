/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.jreadline.console.redirection;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public enum Redirection {
    PIPE("|"),
    PIPE_OUT_AND_ERR("|&"),
    OVERWRITE_OUT(">"),
    APPEND_OUT(">>"),
    OVERWRITE_IN("<"),
    OVERWRITE_ERR("2>"),
    APPEND_ERR("2>>"),
    OVERWRITE_OUT_AND_ERR("2>&1"),
    FIRST("");

    private String controlOperator;

    Redirection(String co) {
        this.controlOperator = co;
    }

    public String getControlOperator() {
        return controlOperator;
    }

}
