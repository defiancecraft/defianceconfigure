package com.defiancecraft.configure.util;

/**
 * Really simple log implementation, logging if output is verbose
 * and verbose logging is enabled, or if the output is not verbose.
 */
public class Logger {

	private static boolean verbose = false;
	
	public static void setVerbose(boolean verbose) {
		Logger.verbose = verbose;
	}
	
	public static void log(String msg) {
		log(msg, false);
	}
	
	public static void log(String msg, boolean verbose) {
		if ((verbose && Logger.verbose) || !verbose)
			System.out.println(msg);
	}
	
	public static void log(String msg, boolean verbose, Object... params) {
		if ((verbose && Logger.verbose) || !verbose)
			System.out.println(String.format(msg, params));
	}
	
}
