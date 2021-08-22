package com.shimizukenta.secs.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shimizukenta.secs.ByteArrayProperty;
import com.shimizukenta.secs.hsmsgs.AbstractHsmsGsRebindPassiveCommunicator;

public class SecsUtils {

	/**
	 * 通过设备id 来获取请求头
	 * 
	 * @param deviceId
	 * @return
	 */
	public final static ByteArrayProperty changeDeviceid(int deviceId) {
		ByteArrayProperty sessionIdBytes = ByteArrayProperty.newInstance(new byte[] { 0, 0 });

		int v = deviceId;
		byte[] bs = new byte[] { (byte) (v >> 8), (byte) v };
		sessionIdBytes.set(bs);

		return sessionIdBytes;

	}

	/**
	 * 将设备id 进行md5 混淆取得数字作为回话id
	 * 
	 * @param str
	 * @return
	 */
	public static int deviceIdStrToInt(String str) {

		String md5 = md5(str);
		
		return getNumbers(md5);
	}

	private static String md5(String input) {
		if (input == null || input.length() == 0) {
			input = Objects.toString(input);
		}
		try {
			MessageDigest md5 = MessageDigest.getInstance("MD5");
			md5.update(input.getBytes());
			byte[] byteArray = md5.digest();

			StringBuilder sb = new StringBuilder();
			for (byte b : byteArray) {
				// 一个byte格式化成两位的16进制，不足两位高位补零
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	// 截取数字
	public static int getNumbers(String str) {
		if (str == null || str.length() == 0)
			return 0;

		String regEx = "[^0-9]";

		Pattern p = Pattern.compile(regEx);

		Matcher m = p.matcher(str);
		String trim = m.replaceAll("").trim();
		Long parseLong = Long.valueOf(trim.substring(0, 8));
		if(parseLong < 0) {
			parseLong = parseLong * (-1);
		}
		return  parseLong.intValue();

	}

	/** 当前机台是否连接成功
	 * @param machineId
	 * @return
	 */
	public static boolean isActiveConnection(String machineId) {
		int deviceIdStrToInt = deviceIdStrToInt(machineId);
		boolean contains = AbstractHsmsGsRebindPassiveCommunicator.deviceIdConnections.keySet().contains(deviceIdStrToInt);
		return contains;
	}
}
