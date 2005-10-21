/**
 * 
 */
package org.gusdb.wdk.model.xml;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.gusdb.wdk.model.WdkModel;
import org.gusdb.wdk.model.WdkModelException;

/**
 * @author Jerric
 * @created Oct 11, 2005
 */
public class XmlQuestion {

    private String name;
    private String displayName;
    private String recordClassRef;
    private String xmlData;
    private String summaryAttributesRef;
    private XmlAttributeField[] summaryAttributes;
    private String description;
    private String help;

    private XmlQuestionSet questionSet;
    private XmlRecordClass recordClass;
    private XmlAnswer answer;
    private XmlDataLoader loader;
    private WdkModel model;

    /**
     * 
     */
    public XmlQuestion() {
    // Initialize member variables
    }

    /**
     * @return Returns the description.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * @param description The description to set.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return Returns the displayName.
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * @param displayName The displayName to set.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return Returns the help.
     */
    public String getHelp() {
        return this.help;
    }

    /**
     * @param help The help to set.
     */
    public void setHelp(String help) {
        this.help = help;
    }

    /**
     * @return Returns the name.
     */
    public String getName() {
        return this.name;
    }

    public String getFullName() {
        if (questionSet == null) return getName();
        else return questionSet.getName() + "." + getName();
    }

    /**
     * @param name The name to set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @param summaryAttributesRef The summaryAttributesRef to set.
     */
    public void setSummaryAttributesRef(String summaryAttributesRef) {
        this.summaryAttributesRef = summaryAttributesRef;
    }

    public XmlAttributeField[] getSummaryAttributes() {
        return summaryAttributes;
    }

    /**
     * @param xmlData
     */
    public void setXmlDataURL(String xmlData) {
        this.xmlData = xmlData;
        this.answer = null; // reset the cache
    }

    public String getXmlDataURL() {
        return xmlData;
    }

    public XmlQuestionSet getQuestionSet() {
        return questionSet;
    }

    public void setQuestionSet(XmlQuestionSet questionSet) {
        this.questionSet = questionSet;
    }

    /**
     * @return Returns the recordClass.
     */
    public XmlRecordClass getRecordClass() {
        return this.recordClass;
    }

    /**
     * @param recordClassRef The recordClassRef to set.
     */
    public void setRecordClassRef(String recordClassRef) {
        this.recordClassRef = recordClassRef;
    }

    public void resolveReferences(WdkModel model) throws WdkModelException {
        // resolve the reference to XmlRecordClass
        recordClass = (XmlRecordClass) model.resolveReference(recordClassRef,
                name, "question", "recordClassRef");

        // resolve the references to summary attributes
        if (summaryAttributesRef == null) { // default use all attribute fields
            summaryAttributes = recordClass.getAttributeFields();
        } else { // use a subset of attribute fields
            Map<String, XmlAttributeField> summaries = new HashMap<String, XmlAttributeField>();
            String[] names = summaryAttributesRef.split(",");
            for (String name : names) {
                try {
                    XmlAttributeField field = recordClass.getAttributeField(name);
                    summaries.put(field.getName(), field);
                } catch (WdkModelException ex) {
                    // TODO Auto-generated catch block
                    ex.printStackTrace();
                    // System.err.println(ex);
                }
            }
            summaryAttributes = new XmlAttributeField[summaries.size()];
            summaries.values().toArray(summaryAttributes);
        }
    }

    public void setResources(WdkModel model) {
        // initialize data loader
        loader = new XmlDataLoader(model.getXmlSchemaURL());
        this.model = model;
    }

    public XmlAnswer makeAnswer(Map<String, String> params, int startIndex,
            int endIndex) throws WdkModelException {
        if (answer == null) { // parse xml and create an answertaPa
            URL xmlDataURL;
            try {
                if (xmlData.startsWith("http://")
                        || xmlData.startsWith("ftp://")
                        || xmlData.startsWith("https://")) {
                    xmlDataURL = new URL(xmlData);
                } else {
                    File xmlDataDir = model.getXmlDataDir();
                    File xmlDataFile = new File(xmlDataDir, xmlData);
                    xmlDataURL = xmlDataFile.toURL();
                }
                answer = loader.parseDataFile(xmlDataURL);
            } catch (MalformedURLException ex) {
                throw new WdkModelException(ex);
            }
        }
        // assign start & end index
        answer.setStartIndex((startIndex <= endIndex) ? startIndex : endIndex);
        answer.setEndIndex((startIndex <= endIndex) ? endIndex : startIndex);
        answer.setQuestion(this);
        answer.resolveReferences(WdkModel.INSTANCE);
        answer.setResources(WdkModel.INSTANCE);

        return answer;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer("XmlQuestion: name='");
        buf.append(name);
        buf.append("'\r\n\trecordClass='");
        buf.append(recordClassRef);
        buf.append("'\r\n\txmlDataURL='");
        buf.append(xmlData);
        buf.append("'\r\n\tdisplayName='");
        buf.append(getDisplayName());
        buf.append("'\r\n\tdescription='");
        buf.append(getDescription());
        buf.append("'\r\n\thelp='");
        buf.append(getHelp());
        buf.append("'\r\n");
        return buf.toString();
    }
}