/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ceco.lollipop.gravitybox.webserviceclient;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import com.ceco.lollipop.gravitybox.R;

import android.content.Context;

public class RequestParams {

    private Context mContext;
    private String mUrl;
    private String mAction;
    private List<NameValuePair> mParams;

    public RequestParams(Context context) {
        mContext = context;
        mUrl = mContext.getString(R.string.url_web_service);
        mParams = new ArrayList<NameValuePair>();
    }

    public String getUrl() {
        return mUrl;
    }

    public String getAction() {
        return mAction;
    }

    public void setAction(String action) {
        mAction = action;
        mParams.add(new BasicNameValuePair("action", mAction));
    }

    public List<NameValuePair> getParams() {
        return mParams;
    }

    public void addParam(String key, String value) {
        mParams.add(new BasicNameValuePair(key, value));
    }
}
