/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/

package org.jetel.exception;

/**
 * Intended to capture incorrect data
 * @author maciorowski
 *
 */
public class BadDataFormatException extends RuntimeException {
	private String offendingFormat = null;
	/**
	 * 
	 */
	public BadDataFormatException() {
		super();
	}

	/**
	 * @param arg0
	 */
	public BadDataFormatException(String arg0) {
		super(arg0);
	}

	/**
	 * @param arg0
	 * @param offendingFormat
	 */
	public BadDataFormatException(String arg0, String offendingFormat) {
		super(arg0);
		setOffendingFormat(offendingFormat);
	}

	public void setOffendingFormat(String offendingFormat) {
		this.offendingFormat = offendingFormat;
	}

	public String getOffendingFormat() {
		return offendingFormat;
	}
}
