/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package com.cloveretl.tableau;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.jetel.data.Defaults;
import org.jetel.metadata.DataFieldMetadata;
import org.jetel.metadata.DataRecordMetadata;
import org.jetel.util.ExceptionUtils;
import org.jetel.util.string.StringUtils;

import com.tableausoftware.DataExtract.Collation;
import com.tableausoftware.DataExtract.Type;

public class TableauTableStructureParser {
	
	public static final String TABLEAU_COLUMN_DEFINITION_PROPERTY_DELIMITER = ",";//$NON-NLS-1$
	public static final String DEFAULT_COLLATION_STRING = "default";//$NON-NLS-1$
	public static final String DEFAULT_TABLEAU_TYPE_STRING = "automatic"; //$NON-NLS-1$
	
	public static final Collation DEFAULT_COLLATION = Collation.EN_US;

	private HashMap<String, TableauTableColumnDefinition> tableauMapping = new HashMap<String, TableauTableColumnDefinition>();
	DataRecordMetadata inMetadata;
	boolean paramsAllowed;

	private List<String> errors = new ArrayList<String>();

	public TableauTableStructureParser(String tablestructureString, boolean paramsAllowed,
			DataRecordMetadata inMetadata) {
		this.inMetadata = inMetadata;

		this.paramsAllowed = paramsAllowed;

		parseTableStructure(StringUtils.split(tablestructureString));

		// fill in missing values (use defaults)

		for (DataFieldMetadata field : inMetadata.getFields()) {
			String fieldName = field.getName();
			TableauTableColumnDefinition columnDefinition = tableauMapping.get(fieldName);
			if (columnDefinition == null) {
				columnDefinition = new TableauTableColumnDefinition(fieldName, DEFAULT_TABLEAU_TYPE_STRING,
						DEFAULT_COLLATION_STRING);
				tableauMapping.put(fieldName, columnDefinition);
			}
			if (columnDefinition.getTableauType() == null) {
				columnDefinition.setTableauType(DEFAULT_TABLEAU_TYPE_STRING);
			}
			if (columnDefinition.getCollation() == null) {
				columnDefinition.setCollation(DEFAULT_COLLATION_STRING);
			}

			if (!columnDefinition.getTableauType().equals(DEFAULT_TABLEAU_TYPE_STRING)) {
				try {
					checkTypeCompatibility(field,
							Type.valueOf(columnDefinition.getTableauType()));
				} catch (NoClassDefFoundError e) {
					errors.add("Tableau libraries need to be set on environment variable PATH!");
				}
			}
		}
	}

	private void checkTypeCompatibility(DataFieldMetadata field,
			Type tableauType) {
		if (tableauType == null) {
			errors.add("Invalid tableau type set for field: " + field.getName());
			return;
		}
		if (!Arrays.asList(TableauWriter.getCompatibleTypes(field)).contains(
				tableauType)) {
			errors.add("Incompatible types set for field " + field.getName()
					+ ": " + field.getDataType() + "," + tableauType.name());
			return;
		}

	}

	private void parseTableStructure(String[] columns) {
		for (String expression : columns) {
			String expr2 = expression.trim();
			if (expr2.equals("")) {
				continue;
			}

			try {
				parseColumnDefinition(expr2);
			} catch (Exception e) {
				String message = "Invalid tableStructure '" + expr2 + "'";
				errors.add(ExceptionUtils.getMessage(message, e));
			}
		}
	}

	private void parseColumnDefinition(String expr) {
		String[] parsedExpression = expr.split(Defaults.ASSIGN_SIGN);
		String inputField = parsedExpression[0].trim().substring(
				Defaults.CLOVER_FIELD_INDICATOR.length()); // skip the leading "$"

		parsedExpression = parsedExpression[1]
				.split(TABLEAU_COLUMN_DEFINITION_PROPERTY_DELIMITER);

		String tableauType = parsedExpression[0].trim();
		String collation = parsedExpression[1].trim();

		TableauTableColumnDefinition columnDefinition = new TableauTableColumnDefinition(inputField, tableauType,
				collation);

		tableauMapping.put(inputField, columnDefinition);
	}

	public HashMap<String,TableauTableColumnDefinition> getTableauMapping() {
		return tableauMapping;
	}

	public List<String> getErrors() {
		return errors;
	}

	public static class TableauTableColumnDefinition {
		private String collation;
		private String name;
		private String tableauType;

		public TableauTableColumnDefinition(String name, String tableauType,
				String collation) {
			super();
			this.collation = collation;
			this.name = name;
			this.tableauType = tableauType;
		}

		public String getCollation() {
			return collation;
		}

		public void setCollation(String collation) {
			this.collation = collation;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getTableauType() {
			return tableauType;
		}

		public void setTableauType(String tableauType) {
			this.tableauType = tableauType;
		}
	}
}
