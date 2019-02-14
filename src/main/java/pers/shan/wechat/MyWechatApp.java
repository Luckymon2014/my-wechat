package pers.shan.wechat;

import lombok.extern.slf4j.Slf4j;
import pers.shan.wechat.common.Constant;
import pers.shan.wechat.common.QRCodeUtils;
import pers.shan.wechat.common.StringUtils;
import pers.shan.wechat.common.WeChatException;
import pers.shan.wechat.entity.LoginSession;
import pers.shan.wechat.http.HttpClient;
import pers.shan.wechat.http.request.BaseRequest;
import pers.shan.wechat.http.request.FileRequest;
import pers.shan.wechat.http.request.StringRequest;
import pers.shan.wechat.http.response.ApiResponse;
import pers.shan.wechat.http.response.FileResponse;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: shanxiao
 * @date: 2019/02/13
 */
@Slf4j
public class MyWechatApp {

    private static final Pattern UUID_PATTERN = Pattern.compile("window.QRLogin.code = (\\d+); window.QRLogin.uuid = \"(\\S+?)\";");
    private static final Pattern CHECK_LOGIN_PATTERN = Pattern.compile("window.code=(\\d+)");
    private static final Pattern PROCESS_LOGIN_PATTERN = Pattern.compile("window.redirect_uri=\"(\\S+)\";");

    private HttpClient client = new HttpClient();
    private LoginSession loginSession = new LoginSession();
    private String uuid;

    public static void main(String[] args) {

        MyWechatApp app = new MyWechatApp();
        // 访问微信网页版首页
        app.initPage();
        // 获取一个随机uuid
        String uuid = app.getUUID();
        log.info("uuid: " + uuid);
        // 根据uuid下载登录二维码
        app.getQrImage(uuid, "assets", false);
        // 循环等待扫码并确认登录
        log.info("扫码和确认登录...");
        while (true) {
            String status = app.checkLogin(uuid);
            if (Constant.STATE_SUCCESS.equals(status)) {
                log.info("登录成功！");
                break;
            } else if ("201".equals(status)) {
                log.info("请在手机上确认登录");
                continue;
            } else if ("408".equals(status)) {
                log.info("登录超时");
                continue;
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new WeChatException(e);
            }
        }
//        5.此时可以获取联系人列表，可以发送消息。然后不断调用同步接口。
//        6.如果同步接口有返回，则可以获取新消息，然后继续调用同步接口。
    }

    private void initPage() {
        log.info("访问微信网页版首页");
        this.client.send(new StringRequest("https://wx.qq.com/"));
    }

    private String getUUID() {
        log.info("获取UUID");
        ApiResponse response = this.client.send(
                new StringRequest("https://login.weixin.qq.com/jslogin")
                        .add("appid", "wx782c26e4c19acffb")
                        .add("fun", "new")
                        .add("lang", "zh_CN")
                        .add("_", System.currentTimeMillis())
        );
        Matcher matcher = UUID_PATTERN.matcher(response.getRawBody());
        if (matcher.find() && Constant.STATE_SUCCESS.equals(matcher.group(1))) {
            this.uuid = matcher.group(2);
        }
        return this.uuid;
    }

    private void getQrImage(String uuid, String qrImgDir, boolean terminalShow) throws WeChatException {
        log.info("下载二维码");
        uuid = null == uuid ? this.uuid : uuid;
        FileResponse fileResponse = this.client.download(
                new FileRequest("https://login.weixin.qq.com/qrcode/" + uuid));
        // 保存二维码图片
        InputStream inputStream = fileResponse.getInputStream();
        OutputStream outputStream = null;
        File qrCode;
        try {
            File dir = new File(qrImgDir);
            if (!dir.isDirectory()) {
                dir.mkdirs();
            }
            qrCode = new File(dir, "qrImg.png");
            if (qrCode.exists()) {
                qrCode.delete();
            }
            outputStream = new FileOutputStream(qrCode);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        } catch (Exception e) {
            throw new WeChatException(e);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                throw new WeChatException(e);
            }
        }
        // 展示二维码图片
        try {
            QRCodeUtils.showQrCode(qrCode, terminalShow);
        } catch (Exception e) {
            this.getQrImage(uuid, qrImgDir, terminalShow);
        }
    }

    private String checkLogin(String uuid) {
        uuid = null == uuid ? this.uuid : uuid;
        Long time = System.currentTimeMillis();
        ApiResponse response = this.client.send(
                new StringRequest("https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login")
                        .add("loginicon", true)
                        .add("uuid", uuid)
                        .add("tip", "0")
                        .add("_", time)
                        .add("r", (int) (-time / 1000) / 1579)
                        .timeout(30));
        Matcher matcher = CHECK_LOGIN_PATTERN.matcher(response.getRawBody());
        if (matcher.find()) {
            if (Constant.STATE_SUCCESS.equals(matcher.group(1))) {
                if (!this.processLoginSession(response.getRawBody())) {
                    return Constant.STATE_FAIL;
                }
                return Constant.STATE_SUCCESS;
            }
            return matcher.group(1);
        }
        return Constant.STATE_FAIL;
    }

    private boolean processLoginSession(String loginContent) {
        Matcher matcher = PROCESS_LOGIN_PATTERN.matcher(loginContent);
        if (matcher.find()) {
            loginSession.setUrl(matcher.group(1));
        }
        String url = loginSession.getUrl();
        ApiResponse response = this.client.send(new StringRequest(url).noRedirect());
        loginSession.setUrl(loginSession.getUrl().substring(0, loginSession.getUrl().lastIndexOf("/")));

        String body = response.getRawBody();
        List<String> fileUrl = new ArrayList<>();
        List<String> syncUrl = new ArrayList<>();
        for (int i = 0; i < Constant.FILE_URL.size(); i++) {
            fileUrl.add(String.format("https://%s/cgi-bin/mmwebwx-bin", Constant.FILE_URL.get(i)));
            syncUrl.add(String.format("https://%s/cgi-bin/mmwebwx-bin", Constant.WEB_PUSH_URL.get(i)));
        }
        boolean flag = false;
        for (int i = 0; i < Constant.FILE_URL.size(); i++) {
            String indexUrl = Constant.INDEX_URL.get(i);
            if (loginSession.getUrl().contains(indexUrl)) {
                loginSession.setFileUrl(fileUrl.get(i));
                loginSession.setSyncUrl(syncUrl.get(i));
                flag = true;
                break;
            }
        }
        if (!flag) {
            loginSession.setFileUrl(loginSession.getUrl());
            loginSession.setSyncUrl(loginSession.getUrl());
        }

        loginSession.setDeviceId("e" + System.currentTimeMillis());

        BaseRequest baseRequest = new BaseRequest();
        loginSession.setBaseRequest(baseRequest);

        loginSession.setSKey(StringUtils.match("<skey>(\\S+)</skey>", body));
        loginSession.setWxSid(StringUtils.match("<wxsid>(\\S+)</wxsid>", body));
        loginSession.setWxUin(StringUtils.match("<wxuin>(\\S+)</wxuin>", body));
        loginSession.setPassTicket(StringUtils.match("<pass_ticket>(\\S+)</pass_ticket>", body));

        baseRequest.setSkey(loginSession.getSKey());
        baseRequest.setSid(loginSession.getWxSid());
        baseRequest.setUin(loginSession.getWxUin());
        baseRequest.setDeviceID(loginSession.getDeviceId());
        return true;
    }
}
