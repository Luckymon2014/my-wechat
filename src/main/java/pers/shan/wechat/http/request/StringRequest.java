package pers.shan.wechat.http.request;

import pers.shan.wechat.http.response.ApiResponse;

/**
 * @author biezhi
 * @date 2018/1/18
 */
public class StringRequest extends ApiRequest<StringRequest, ApiResponse> {

    public StringRequest(String url) {
        super(url, ApiResponse.class);
    }

}