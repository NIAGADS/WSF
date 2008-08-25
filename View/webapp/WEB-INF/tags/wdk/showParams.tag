<%-- 
display the parameter values for an non-boolean answer.
--%>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="bean" uri="http://jakarta.apache.org/struts/tags-bean" %>
<%@ taglib prefix="html" uri="http://jakarta.apache.org/struts/tags-html" %>

<%@ attribute name="wdkAnswer"
              type="org.gusdb.wdk.model.jspwrap.AnswerBean"
              required="true"
              description="Answer Object"
%>

<div ${paddingStyle}>
    <!-- simple question -->
    <c:set value="${wdkAnswer.internalParams}" var="params"/>
    <c:set value="${wdkAnswer.question.paramsMap}" var="qParamsMap"/>
    <c:set value="${wdkAnswer.question.displayName}" var="wdkQuestionName"/>
    <table border="0" cellspacing="0" cellpadding="0">
        <tr>
            <td align="right" valign="top" class="medium"><b>Query</b></td>
            <td valign="top" class="medium">&nbsp;:&nbsp;</td>
            <td class="medium">${wdkQuestionName}</td>
       </tr>
       <tr>
            <td align="right" valign="top" class="medium"><b>Parameter</b></td>
            <td valign="top" class="medium">&nbsp;:&nbsp;</td>
            <td>
                <table border="0" cellspacing="0" cellpadding="0">
                    <c:forEach items="${qParamsMap}" var="p">
                       <c:set var="pNam" value="${p.key}"/>
                       <c:set var="qP" value="${p.value}"/>
                       <c:set var="aP" value="${params[pNam]}"/>
                       <c:if test="${qP.isVisible}">
                          <tr>
                             <td align="right" valign="top" nowrap class="medium"><b><i>${qP.prompt}</i><b></td>
                             <td valign="top" class="medium">&nbsp;:&nbsp;</td>
                             <td class="medium">
                                <c:choose>
                                   <c:when test="${qP.class.name eq 'org.gusdb.wdk.model.jspwrap.DatasetParamBean'}">
                                      <jsp:setProperty name="qP" property="combinedId" value="${aP}" />
                                      <c:set var="dataset" value="${qP.dataset}" />  
                                      "${dataset.summary}"
                                      <c:if test='${fn:length(dataset.uploadFile) > 0}'>
                                         from file &lt;${dataset.uploadFile}&gt;
                                      </c:if>
                                   </c:when>
                                   <c:when test="${qP.class.name eq 'org.gusdb.wdk.model.jspwrap.HistoryParamBean'}">
                                      <jsp:setProperty name="qP" property="combinedId" value="${aP}" />
                                      <c:set var="subHistory" value="${qP.history}" />
                                      History #${subHistory.historyId}: ${subHistory.customName}
                                   </c:when>
                                   <c:otherwise>
                                      <jsp:setProperty name="qP" property="paramValue" value="${aP}" />
                                      <jsp:setProperty name="qP" property="truncateLength" value="1000" />
                                      ${qP.decompressedValue}
                                   </c:otherwise>
                                </c:choose>
                             </td>
                          </tr>
                       </c:if>
                    </c:forEach>
                    
                    <%-- display subType information, if having any --%>
                    <c:set var="recordClass" value="${wdkAnswer.question.recordClass}"/>
                    <c:if test="${recordClass.hasSubType}">
                        <c:set var="subTypeParam" value="${recordClass.subType.subTypeParam}"/>
                        <tr>
                            <td align="right" valign="top" nowrap class="medium">
                                <b><i>${subTypeParam.prompt}</i><b>
                            </td>
                            <td valign="top" class="medium">&nbsp;:&nbsp;</td>
                            <td class="medium">
                                <jsp:setProperty name="subTypeParam" 
                                                 property="paramValue" 
                                                 value="${wdkAnswer.subTypeValue}" />
                                <jsp:setProperty name="subTypeParam" 
                                                 property="truncateLength" 
                                                 value="1000" />
                                ${subTypeParam.decompressedValue}
                            </td>
                        </tr>
                    </c:if>
                </table>
            </td>
        </tr>
    </table>
</div>