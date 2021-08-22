package utils;

import com.shimizukenta.secs.utils.SecsUtils;

public class TestSecsUtils {

	
	public static void main(String[] args) {
		
		int stringToInt = SecsUtils.deviceIdStrToInt(null);
		System.out.println(" stringToInt :" + stringToInt);
		
		int aa = SecsUtils.deviceIdStrToInt("TAd0");
		System.out.println(" aa :" + aa);
		
		int a = SecsUtils.deviceIdStrToInt("0");
		System.out.println(" a :" + a);
		
		int b = SecsUtils.deviceIdStrToInt( "bb");
		System.out.println(" b :" + b);
	}
}
