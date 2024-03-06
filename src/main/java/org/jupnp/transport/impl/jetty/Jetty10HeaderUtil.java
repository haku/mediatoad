/*
 * Copyright (C) 2011-2024 4th Line GmbH, Switzerland and others
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: CDDL-1.0
 */
package org.jupnp.transport.impl.jetty;

import java.util.List;
import java.util.Map;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpField;
import org.jupnp.http.Headers;

/**
 * org.jupnp.transport.impl.jetty.HeaderUtil modified for Jetty 10.
 *
 * @author Christian Bauer
 * @author Victor Toni
 */
public class Jetty10HeaderUtil {

    private Jetty10HeaderUtil() {
        // no instance of this class
    }

    /**
     * Add all jUPnP {@link Headers} header information to {@link Request}.
     *
     * @param request to enrich with header information
     * @param headers to be added to the {@link Request}
     */
    public static void add(final Request request, final Headers headers) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (final String value : entry.getValue()) {
                request.headers(h-> h.add(entry.getKey(), value));
            }
        }
    }

    /**
     * Get all header information from {@link Response} jUPnP {@link Headers}.
     *
     * @param response {@link Response}, must not be null
     * @return {@link Headers}, never {@code null}
     */
    public static Headers get(final Response response) {
        final Headers headers = new Headers();
        for (HttpField httpField : response.getHeaders()) {
            headers.add(httpField.getName(), httpField.getValue());
        }

        return headers;
    }

}
