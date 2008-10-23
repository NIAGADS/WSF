/**
 * 
 */
package org.gusdb.wdk.model;

import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONException;

/**
 * @author Jerric Gao
 * 
 */
public class TextAttributeValue extends AttributeValue {

    private AttributeValueContainer container;
    private TextAttributeField field;
    private String text;

    /**
     * @param attributeValueContainer
     * @param field
     */
    public TextAttributeValue(TextAttributeField field,
            AttributeValueContainer container) {
        super(field);
        this.field = field;
        this.container = container;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.AttributeValue#getValue()
     */
    @Override
    public Object getValue() throws WdkModelException,
            NoSuchAlgorithmException, SQLException, JSONException,
            WdkUserException {
        if (this.text == null) {
            String text = field.getText();
            Map<String, AttributeField> subFields = field.parseFields(text);
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            for (String subField : subFields.keySet()) {
                AttributeValue value = container.getAttributeValue(subField);
                values.put(subField, value.getValue());
            }
            this.text = Utilities.replaceMacros(text, values);
        }
        return this.text;
    }
}