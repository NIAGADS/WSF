/**
 * 
 */
package org.gusdb.wdk.model.jspwrap;

import java.util.Date;

import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.user.Dataset;

/**
 * @author xingao
 * 
 */
public class DatasetBean {

    private Dataset dataset;

    public DatasetBean(Dataset dataset) {
        this.dataset = dataset;
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.user.Dataset#getCreateTime()
     */
    public Date getCreateTime() {
        return dataset.getCreateTime();
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.user.Dataset#getDatasetId()
     */
    public int getDatasetId() {
        return dataset.getDatasetId();
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.user.Dataset#getSize()
     */
    public int getSize() {
        return dataset.getSize();
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.user.Dataset#getSummary()
     */
    public String getSummary() {
        return dataset.getSummary();
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.user.Dataset#getUploadFile()
     */
    public String getUploadFile() {
        return dataset.getUploadFile();
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.user.Dataset#getUser()
     */
    public UserBean getUser() {
        return new UserBean(dataset.getUser());
    }

    /**
     * @return
     * @throws WdkUserException
     * @see org.gusdb.wdk.model.user.Dataset#getValues()
     */
    public String[] getValues() throws WdkUserException {
        return dataset.getValues();
    }

    /**
     * @return
     * @throws WdkUserException
     * @throws WdkUserException
     * @see org.gusdb.wdk.model.user.Dataset#getValue()
     */
    public String getValue() throws WdkUserException {
        try {
            return dataset.getValue();
        } catch (WdkUserException ex) {
            // TEST Auto-generated catch block
            ex.printStackTrace();
            throw ex;
        }
    }

    /**
     * @return
     * @see org.gusdb.wdk.model.user.Dataset#getChecksum()
     */
    public String getChecksum() {
        return dataset.getChecksum();
    }
}