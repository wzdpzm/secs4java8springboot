package com.shimizukenta.secs.ext.config;




import java.io.Serializable;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


/** hsms通讯参数
 * @ClassName: HsmsProps
 * @Description: hsms 通讯
 * @author dsy
 * @date 2021年6月23日
 *
 */
@AllArgsConstructor
@NoArgsConstructor
@ConfigurationProperties(ignoreInvalidFields = true, prefix = "additions.devices.hsms")
@Builder
@Data
public class HsmsCplexProps  implements Serializable {



  
	
    /**
	 * @Fields 序列化
	 */
	private static final long serialVersionUID = 1L;

	/**
	 *  多个通讯器,这里主要指 主动模式下多个,没动模式下多个暂不考虑
	 */
	@Builder.Default
	private Boolean mutiple = false ;
	
	/**
	 * 启用
	 */
	private Boolean enabled ;
	
	private HsmsProps props;
	
	 /**
	 * 主动模式下多个回话, 被动模式下暂不考虑
	 */
	private Map<String, HsmsProps> sessions;

	
	
	

}
