/**
 * 
 */
package animo.analyser;

import animo.exceptions.ANIMOException;

/**
 * This exceptions is thrown to indicate that something went wrong while
 * analysing the model.
 * 
 * @author B. Wanders
 */
public class AnalysisException extends ANIMOException {

	private static final long serialVersionUID = 8207531416480612466L;

	/**
	 * Constructor with detail message and cause.
	 * 
	 * @param message the detail message
	 * @param cause the cause
	 */
	public AnalysisException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * Constructor with detail message.
	 * 
	 * @param message the detail message
	 */
	public AnalysisException(String message) {
		super(message);
	}
}
