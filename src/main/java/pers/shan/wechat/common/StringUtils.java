package pers.shan.wechat.common;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字符串工具类
 */
public class StringUtils {

    public static boolean isEmpty(String value) {
        return null == value || value.isEmpty();
    }

    public static boolean isNotEmpty(String value) {
        return null != value && !value.isEmpty();
    }

    public static String random(int count) {
        RandomString gen = new RandomString(count, new Random());
        return gen.nextString();
    }

    /**
     * 正则匹配
     *
     * @param reg
     * @param text
     * @return
     */
    public static String match(String reg, String text) {
        Pattern pattern = Pattern.compile(reg);
        Matcher m       = pattern.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

}
