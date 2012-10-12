/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.willowtreeapps.teamcity.testflight;

import org.apache.http.HttpResponse;

/**
 * Created by IntelliJ IDEA.
 * User: jbeck
 * Date: 10/11/12
 * Time: 4:14 PM
 */

public class UploadException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;
    private final HttpResponse response;

    public UploadException(int statusCode, String responseBody, HttpResponse response) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.response = response;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public HttpResponse getResponse() {
        return response;
    }
}