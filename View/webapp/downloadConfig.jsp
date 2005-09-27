<%@ taglib prefix="site" tagdir="/WEB-INF/tags/site" %>
<%@ taglib prefix="wdk" tagdir="/WEB-INF/tags/wdk" %>
<%@ taglib prefix="pg" uri="http://jsptags.com/tags/navigation/pager" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="html" uri="http://jakarta.apache.org/struts/tags-html" %>
<%@ taglib prefix="nested" uri="http://jakarta.apache.org/struts/tags-nested" %>

<!-- get wdkAnswer from requestScope -->
<c:set value="${sessionScope.wdkAnswer}" var="wdkAnswer"/>

<!-- display page header -->
<site:header banner="Create and download a report" />

<!-- display description for page -->
<p><b>Generate a tab delimited text report of selected attributes for the question below, with an optional header line of attribute names.</b></p>

<!-- display question and param values and result size for wdkAnswer -->
<c:choose>
  <c:when test="${wdkAnswer.isBoolean}">
    <!-- boolean question -->

    <table><tr><td valign="top" align="left"><b>Expanded Question:</b></td>
               <td valign="top" align="left">
                 <nested:root name="wdkAnswer">
                   <jsp:include page="/WEB-INF/includes/bqShowNode.jsp"/>
                 </nested:root>
               </td></tr>
    </table>
  </c:when>
  <c:otherwise>
    <!-- simple question -->
    <c:set value="${wdkAnswer.params}" var="params"/>
    <c:set value="${wdkAnswer.question.displayName}" var="wdkQuestionName"/>
    <table><tr><td valign="top" align="left"><b>Query:</b></td>
               <td valign="top" align="left">${wdkQuestionName}</td></tr>
           <tr><td valign="top" align="left"><b>Parameters:</b></td>
               <td valign="top" align="left">
                 <table>
                   <c:forEach items="${params}" var="p">
                     <tr><td align="right">${p.key}:</td><td><i>${p.value}</i></td></tr> 
                   </c:forEach>
                 </table></td></tr>
    </table>
  </c:otherwise>
</c:choose>

<hr>

<!-- handle empty result set situation -->
<c:choose>
  <c:when test='${wdkAnswer.resultSize == 0}'>
    No results for your query
  </c:when>
  <c:otherwise>

<!-- content of current page -->
<html:form method="get" action="configDownload">
  <table>
  <tr><td><b>Choose attributes: </b></td>
      <td><html:multibox property="selectedFields">all</html:multibox>
          All <I><B>Answer</B></I> summary fields, or select from <I><B>Record</B></I> fields:<br>
          <!--c:forEach items="${wdkAnswer.summaryAttributeNames}" var="recAttrName"-->
          <c:forEach items="${wdkAnswer.question.recordClass.attributeFields}" var="recAttr">
          <c:set value="${recAttr.key}" var="recAttrName"/>

            <c:set value="${wdkAnswer.question.recordClass.attributeFields[recAttrName]}" var="recAttr"/>
            <c:if test="${!recAttr.isInternal}">
              <html:multibox property="selectedFields">
                ${recAttr.name}
              </html:multibox>
              ${recAttr.displayName}
            </c:if>
          </c:forEach>
        </td></tr>
  <tr><td><b>Choose header line: </b></td>
      <td><html:radio property="includeHeader" value="yes">include</html:radio>
          <html:radio property="includeHeader" value="no">exclude</html:radio>
        </td></tr>
  <tr><td colspan="2">&nbsp;</td></tr>
  <tr><td></td>
      <td><html:submit property="downloadConfigSubmit" value="Continue"/>
          <html:reset property="downloadConfigReset" value="Reset"/>
      </td></tr></table>
</html:form>

  </c:otherwise>
</c:choose>

<site:footer/>