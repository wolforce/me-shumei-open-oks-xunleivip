package me.shumei.open.oks.xunleivip;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
    String resultFlag = "false";
    String resultStr = "未知错误！";
    
    boolean needCode = false;
    //获取是否需要填写验证码的URL
    String checkVerifycodeUrl = "";
    //登录账号的URL
    String loginUrl = "";
    //打卡签到的URL
    String signUrl = "";
    //得到当天签到积分的URL
    String getDayScoreUrl = "";
    //验证码URL
    String captchaUrl = "";
    //验证码字符
    String verifycode = "";
    //用户名
    String user = "";
    //密码
    String pwd = "";
    
    /**
     * <p><b>程序的签到入口</b></p>
     * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
     * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
     * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
     * @param cfg “配置”栏内输入的数据
     * @param user 用户名
     * @param pwd 解密后的明文密码
     * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
     */
    public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
        //把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
        CaptchaUtil.context = ctx;
        //标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
        CaptchaUtil.isAutoSign = isAutoSign;
        
        this.user = user;
        this.pwd = pwd;
        
        try{
            //存放Cookies的HashMap
            HashMap<String, String> cookies = new HashMap<String, String>();
            //Jsoup的Response
            Response res;
            
            long randNum = new Date().getTime();
            checkVerifycodeUrl = "http://login.xunlei.com/check?u=" + user + "&cachetime=" + randNum;
            loginUrl = "http://login.xunlei.com/sec2login?cachetime=" + randNum;
            signUrl = "";
            captchaUrl = "http://verify3.xunlei.com/image?cachetime=" + randNum;

            
            //访问服务器查看是否要填写验证码，此返回的信息存放在名为check_result的Cookie里，并不是在网页文字上，形式为  0：!UEG
            //第一个字如果0是就不用填写验证码，其他的数就要填
            res = Jsoup.connect(checkVerifycodeUrl).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
            cookies.putAll(res.cookies());
            System.out.println(cookies);
            
            String[] checkStrArr = cookies.get("check_result").split(":");
            System.out.println(checkStrArr[1]);
            int check_result = Integer.valueOf(checkStrArr[0]);
            if (check_result == 0) {
                //没有遇到验证码
                verifycode = checkStrArr[1];
                submitSignin(cookies);
            } else {
                //碰到验证码
                needCode = true;
                if (CaptchaUtil.showCaptcha(captchaUrl, UA_ANDROID, cookies, "迅雷VIP签到", user, "登录需要验证码")) {
                    verifycode = CaptchaUtil.captcha_input;
                    if(verifycode.length() > 0) {
                        //获取验证码成功，可以用CaptchaUtil.captcha_input继续做其他事了
                        submitSignin(cookies);
                    } else {
                        //用户取消输入验证码
                        this.resultFlag = "false";
                        this.resultStr = "用户取消输入验证码，登录失败";
                        return new String[]{resultFlag, resultStr};
                    }
                } else {
                    //拉取验证码失败，签到失败
                    this.resultFlag = "false";
                    this.resultStr = "拉取登录时的验证码失败";
                    return new String[]{resultFlag, resultStr};
                }
            }
        } catch (IOException e) {
            this.resultFlag = "false";
            this.resultStr = "连接超时";
            e.printStackTrace();
        } catch (Exception e) {
            this.resultFlag = "false";
            this.resultStr = "未知错误！";
            e.printStackTrace();
        }
        
        return new String[]{resultFlag, resultStr};
    }
    
    
    
    /**
     * 提交签到数据
     * @param cookies
     * @throws Exception
     */
    private void submitSignin(HashMap<String, String> cookies) throws Exception
    {
        Response res;
        for(int i=0;i<RETRY_TIMES;i++)
        {
            try {
                //提交登录信息
                res = Jsoup.connect(loginUrl)
                        .data("u", user)
                        .data("p", MD5.md5(MD5.md5(MD5.md5(pwd)) + verifycode.toUpperCase()))
                        .data("verifycode", verifycode)
                        .userAgent(UA_ANDROID)
                        .cookies(cookies)
                        .timeout(TIME_OUT)
                        .referrer(loginUrl)
                        .ignoreContentType(true)
                        .method(Method.POST)
                        .execute();
                cookies.putAll(res.cookies());
                cookies.put("vip_sessionid", "vip_sessionid%3D" + cookies.get("sessionid"));
                
                try {
                    //得到当天签到的积分的URL
                    getDayScoreUrl = "http://jifen.xunlei.com/call?c=user&a=getDaySignScore&userid=" + cookies.get("userid");
                    
                    //得到当天签到的积分，并把返回的cookies保存下来以使有权限访问签到链接
                    res = Jsoup.connect(getDayScoreUrl).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
                    cookies.putAll(res.cookies());
                    System.out.println(res.body());
                    
                    //{"result":0,"message":"ok","data":-2}
                    //{"result":0,"message":"ok","data":1}
                    //data:-1:请求不合法 -2:今天还没有签到 >=0:当前的积分
                    int score = new JSONObject(res.body().replace("var json = ", "")).getInt("data");
                    if(score == -1)
                    {
                        resultFlag = "false";
                        resultStr = "系统忙，请稍后重试！";
                    }
                    else if(score >= 0)
                    {
                        resultFlag = "true";
                        resultStr = "今日已签过到，获得" + score + "积分"; 
                    }
                    else
                    {
                        //打卡签到的URL
                        //String signUrlFinal = "http://jifen.xunlei.com/call?c=user&a=sign&userid=" + cookies.get("userid") + "&rt=" + Math.random();
                        String signUrlFinal = "";
                        String captchaUrl = "http://verify2.xunlei.com/image?cachetime=" + System.currentTimeMillis();
                        if (CaptchaUtil.showCaptcha(captchaUrl, UA_ANDROID, cookies, "迅雷VIP签到", user, "领取积分需要验证码")) {
                            if(verifycode.length() > 0) {
                                //获取验证码成功，可以用CaptchaUtil.captcha_input继续做其他事了
                                signUrlFinal = "http://jifen.xunlei.com/call?c=user&a=sign&verifycode=" + CaptchaUtil.captcha_input + "&userid=" + cookies.get("userid") + "&rt=" + Math.random();
                            } else {
                                //用户取消输入验证码
                                resultFlag = "false";
                                resultStr = "用户取消输入验证码，领取积分失败";
                                return;
                            }
                        } else {
                            //拉取验证码失败，签到失败
                            resultFlag = "false";
                            resultStr = "拉取领取积分时的验证码失败";
                            return;
                        }
                        
                        //访问签到链接
                        res = Jsoup.connect(signUrlFinal).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).referrer(loginUrl).ignoreContentType(true).method(Method.GET).execute();
                        int signScore = new JSONObject(res.body().replace("var json = ", "")).getInt("data");
                        if (signScore >= 0) {
                            resultFlag = "true";
                            resultStr = "签到成功，获得" + signScore + "积分";
                        } else {
                            resultFlag = "false";
                            resultStr = "验证码错误";
                        }
                    }
                    break;//只要提交完成了，就要跳出重试循环
                } catch (Exception e) {
                    e.printStackTrace();
                    this.resultFlag = "false";
                    this.resultStr = "登录成功，签到失败，请检查网络是否正常";
                }
            } catch (Exception e) {
                e.printStackTrace();
                this.resultFlag = "false";
                if(needCode)
                    this.resultStr = "登录失败，请检查验证码是否正确，网络是否正常";
                else
                    this.resultStr = "登录失败，请检查网络是否正常";
            }
        }
    }
    
}
