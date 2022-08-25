package com.shimizukenta.secs;

import java.util.EventListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SecsLog receive Listener.
 * 
 * <p>
 * This interface is used in {@link SecsCommunicator#addSecsLogListener(SecsLogListener)}<br />
 * </p>
 * 
 * @author kenta-shimizu
 *
 */
public interface SecsLogListener extends EventListener {
	
	/**
	 * put received-SecsLog.
	 * 
	 * <p>
	 * Not accept {@code null}
	 * </p>
	 * 
	 * @param log
	 */
	public void received(SecsLog log);
	
}
