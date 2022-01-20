package utils;

import com.shimizukenta.secs.secs2.Secs2;
import com.shimizukenta.secs.secs2.Secs2Exception;
import com.shimizukenta.secs.sml.SmlDataItemParser;
import com.shimizukenta.secs.sml.SmlParseException;

/**
  * @ClassName: TestSml
  * @Description: sml 文件解析
  * @author dsy
  * @date 2022年1月12日 上午10:35:33
  *
  */
public class TestSml {

	
	
	
	public static void main(String[] args) {
		
		try {
			/**
			 * 将字符串解析成secs
			 */
			Secs2 secs2 = parseSs();
			/**
			 * 从secs中解析数据
			 */
			unParse(secs2) ;
			
		} catch (SmlParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	public static void unParse( Secs2 ss) {
		
		try {
			
			System.out.println("# Get value by index");
			System.out.println("getByte(0, 0):\t" + ss.getByte(0, 0));	/* 1 */
			System.out.println("getAscii(1):\t" + ss.getAscii(1));	/* "MESSAGE-1" */
			System.out.println("getBoolean(2, 0):\t" + ss.getBoolean(2, 0));	/* true */
			System.out.println("getAscii(3, 0, 0):\t" + ss.getAscii(3, 0, 0));	/* "KEY-1" */
			System.out.println("getInt(3, 0, 1, 0):\t" + ss.getInt(3, 0 , 1, 0));	/* 100 */
			System.out.println("getInt(3, 0, 1, 1):\t" + ss.getInt(3, 0 , 1, 1));	/* 101 */
			System.out.println("getInt(3, 0, 1, 2):\t" + ss.getInt(3, 0 , 1, 2));	/* 102 */
			System.out.println("getInt(3, 1, 1, 0):\t" + ss.getInt(3, 1 , 1, 0));	/* 200 */
			System.out.println("getInt(3, 2, 1, 0):\t" + ss.getInt(3, 2 , 1, 0));	/* 300 */
			System.out.println("getFloat(4, 0):\t" + ss.getFloat(4, 0));	/* 400.0F */
			System.out.println("get(3):\t" + ss.get(3));	/* <L[3] ...> */
			System.out.println();
			
			System.out.println("# Get Item-Type");
			System.out.println("get() item-type:\t" + ss.get().secs2Item());	/* LIST */
			System.out.println("get(0) item-type:\t" + ss.get(0).secs2Item());	/* BINARY */
			System.out.println("get(1) item-type:\t" + ss.get(1).secs2Item());	/* ASCII */
			System.out.println("get(2) item-type:\t" + ss.get(2).secs2Item());	/* BOOLEAN */
			System.out.println("get(3) item-type:\t" + ss.get(3).secs2Item());	/* LIST */
			System.out.println("get(3, 0, 1) item-type:\t" + ss.get(3, 0, 1).secs2Item());	/* INT4 */
			System.out.println("get(4) item-type:\t" + ss.get(4).secs2Item());	/* FLOAT4 */
			System.out.println();
			
			System.out.println("# Get Size");
			System.out.println("get() size:\t" + ss.get().size());	/* 4 */
			System.out.println("get(0) size:\t" + ss.get(0).size());	/* 1 */
			System.out.println("get(1) size:\t" + ss.get(1).size());	/* 9 */
			System.out.println("get(2) size:\t" + ss.get(2).size());	/* 1 */
			System.out.println("get(3) size:\t" + ss.get(3).size());	/* 3 */
			System.out.println("get(3, 0, 1) size:\t" + ss.get(3, 0, 1).size());	/* 3 */
			System.out.println("get(4) size:\t" + ss.get(4).size());	/* 1 */
			System.out.println();
			
			{
				System.out.println("# Use for-each-loop, getAscii(3, x, 0)");
				
				Secs2 s3 = ss.get(3);
				
				for ( Secs2 s : s3 ) {	/* "KEY-1" *//* "KEY-2" *//* "KEY-3" */
					System.out.println("getAscii(3, x, 0):\t" + s.getAscii(0));
				}
				
				System.out.println();
			}
			
			{
				System.out.println("# Use stream, getInt(3, x, 1, 0)");
				
				Secs2 s3 = ss.get(3);
				
				s3.stream()
				.map(s -> {
					try {
						return s.getInt(1, 0);
					}
					catch ( Secs2Exception e ) {
						throw new RuntimeException(e);
					}
				})
				.map(v -> "getInt(3, x, 1, 0):\t" + v)
				.forEach(System.out::println);	/* 100 *//* 200 *//* 300 */
				
				System.out.println();
			}
		}
		catch ( Throwable t ) {
			t.printStackTrace();
		}
	}

	
/**
  * @Title: parseSs
  * @Description: 将string 解析成secs
  * @param @return
  * @param @throws SmlParseException    设定文件
  * @return Secs2    返回类型
  * @throws
  */
public static Secs2 parseSs() throws SmlParseException {
		
	    Secs2 ss2 = getSs() ;
		final SmlDataItemParser parser = SmlDataItemParser.newInstance();
		
		Secs2 ss = parser.parse( ss2.toString());
		
		System.out.println("build by SML");
		System.out.println(ss);
		System.out.println();
		return ss ;
	}


	private static Secs2 getSs() {
		Secs2 ss = Secs2.list(
				Secs2.binary((byte)1),	/* 0 */
				Secs2.ascii("MESSAGE-1"),	/* 1 */
				Secs2.bool(true),	/* 2 */
				Secs2.list(	/* 3 */
						Secs2.list(	/* 3,0 */
								Secs2.ascii("KEY-1"),	/* 3,0,0 */
								Secs2.int4(100, 101, 102)	/* 3,0,1 */
								),
						Secs2.list(	/* 3,1 */
								Secs2.ascii("KEY-2"),	/* 3,1,0 */
								Secs2.int4(200, 201, 202)	/* 3,1,1 */
								),
						Secs2.list(	/* 3,2 */
								Secs2.ascii("KEY-3"),	/* 3,2,0 */
								Secs2.int4(300, 301, 302)	/* 3,2,1 */
								)
						),
				Secs2.float4(400.0F)
				);
		return ss;
	}

}
