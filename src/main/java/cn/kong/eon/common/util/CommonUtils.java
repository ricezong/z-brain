package cn.kong.eon.common.util;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 通用工具类
 *
 * @author eon-team
 */
public class CommonUtils {

    private CommonUtils() {
    }

    /**
     * 生成不带横线的 UUID
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 计算字符串的 SHA-256 哈希
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("计算 SHA-256 失败", e);
        }
    }

    /**
     * 流式计算 InputStream 的 SHA-256 哈希（大文件不进内存）
     *
     * @param inputStream 输入流（调用方负责关闭外层流，本方法只包装 DigestInputStream）
     * @return 十六进制哈希字符串
     */
    public static String sha256Stream(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(inputStream, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // DigestInputStream 自动更新 digest
                }
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("流式计算 SHA-256 失败", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * 获取文件扩展名（小写）
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * 判断字符串是否为空或空白
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 判断字符串是否非空
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }
}
