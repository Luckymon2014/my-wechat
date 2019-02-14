package pers.shan.wechat.http;

import pers.shan.wechat.http.request.ApiRequest;
import pers.shan.wechat.http.response.ApiResponse;

import java.io.IOException;

public interface Callback<T extends ApiRequest, R extends ApiResponse> {

    void onResponse(T request, R response);

    void onFailure(T request, IOException e);

}