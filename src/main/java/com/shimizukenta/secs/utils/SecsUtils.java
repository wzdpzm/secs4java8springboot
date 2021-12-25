package com.shimizukenta.secs.utils;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.shimizukenta.secs.ByteArrayProperty;
import com.shimizukenta.secs.ext.config.HsmsProps;
import com.shimizukenta.secs.gem.ClockType;
import com.shimizukenta.secs.hsms.HsmsMessage;
import com.shimizukenta.secs.hsms.HsmsMessageType;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicator;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.shimizukenta.secs.hsmsss.HsmsSsProtocol;


public class SecsUtils {

	public final static Map<HsmsSsCommunicator, Boolean> CONNECTION_STATUS_FACTORY = new ConcurrentHashMap<>(16 );

	
	/**随否是数据报文
	 * @param msg
	 * @return
	 */
	public static final boolean dataMessage( HsmsMessage msg) {
		return HsmsMessageType.get(msg) == HsmsMessageType.DATA;
	}
	
	
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


	
	
	/** 配置属性转化为通讯器配置
	 * @param props
	 * @return
	 */
	public  final static HsmsSsCommunicatorConfig getConfig( HsmsProps props) {
		HsmsSsCommunicatorConfig config = new HsmsSsCommunicatorConfig();
		//测试使用
		config.socketAddress(new InetSocketAddress(props.getHost(), props.getPort()));
//		config.socketAddress(new InetSocketAddress(props.getHost(), props.getPort()));
		config.protocol( HsmsSsProtocol.valueOf(props.getProtocol()));
		
//		config.sessionId(10);
//		config.notLinktest();
//		config.deviceId(props.getDeviceId());
		config.isEquip(props.getIsEquip());
		config.timeout().t3( props.getT3());
		config.gem().mdln("MDLN-A");
	    config.gem().softrev("000001");
	    config.gem().clockType(ClockType.A16);
		/**
		 * T3 timeout 即Reply Timeout， 说具体点就是Data Message timeout. 这个最好理解， 就是HSMS 的会话双方，
		 * 当一方发送消息后，等待对方回答的等待时间，即Response Time, 在规定时间内返回则会话成功， 如果没有在规定时间内返回， 则发送端SECS
		 * Driver通常会产生T3 Time out Alarm 通知上层的业务逻辑程序处理错误. 一般 推荐时间一般是 45秒.
		 */
		config.timeout().t5(props.getT5());
		config.timeout().t6(props.getT6());
		config.timeout().t8(props.getT8());
		return config;
	}
	
	/** 判断当前通讯器是否存活
	 * @param communicator
	 * @return
	 */
	public static boolean isActiveConnection(HsmsSsCommunicator communicator) {
		
		return CONNECTION_STATUS_FACTORY.getOrDefault(communicator, false);
	}
	
	
	/** 获得存活的通讯器
	 * @return
	 */
	public static Map<HsmsSsCommunicator, Boolean> getConnectionStatusFactory() {
		
		return CONNECTION_STATUS_FACTORY;
	}
}
