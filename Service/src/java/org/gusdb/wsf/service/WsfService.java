/**
 * 
 */
package org.gusdb.wsf.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;

import org.apache.axis.MessageContext;
import org.apache.axis.transport.http.HTTPConstants;
import org.apache.log4j.Logger;
import org.gusdb.wsf.plugin.Plugin;
import org.gusdb.wsf.plugin.PluginRequest;
import org.gusdb.wsf.plugin.PluginResponse;
import org.gusdb.wsf.plugin.WsfPluginException;
import org.json.JSONException;

/**
 * The WSF Web service entry point.
 * 
 * @author Jerric
 * @created Nov 2, 2005
 */
public class WsfService {

  /**
   * If the WSF is deployed together with WDK, then WDK will set this variable,
   * so that we can use the "local" mode to invoke plugins.
   */
  public static ServletContext SERVLET_CONTEXT;

  private static final Logger logger = Logger.getLogger(WsfService.class);

  private static final Map<String, Plugin> plugins = new LinkedHashMap<String, Plugin>();

  private static final String STORAGE_DIR = "/wsf-storage/";

  private static final long CLEANUP_INTERVAL = 1 * 3600;
  private static final long MAX_CACHE_AGE = 1 * 24 * 3600;

  private static final int ID_RETRY = 100;

  private static long CUMULATIVE_TIME = 0;
  
  private final Random random;

  private final File storageDir;

  private long lastCleanup = 0;

  public WsfService() {
    random = new Random();
    String temp = System.getProperty("java.io.tmpdir", "/tmp");
    storageDir = new File(temp + STORAGE_DIR);
    logger.debug("WSF storage: " + storageDir.getAbsolutePath());
    if (!storageDir.exists() || !storageDir.isDirectory()) {
      if (!storageDir.mkdirs())
        throw new RuntimeException("Cannot create storage directory: "
            + storageDir.getAbsolutePath());

      // assign full permissions to the dir
       Set<PosixFilePermission> permissions = new HashSet<>(
           Arrays.asList(PosixFilePermission.values()));
      try {
        Files.setPosixFilePermissions(storageDir.toPath(), permissions);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
    logger.debug("WsfService initialized");
  }

  /**
   * Invoke a plugin, and send back the result.
   * 
   * @param jsonRequest
   * @return if the result size is bigger than PACKET_SIZE constant, it will be
   *         split into multiple packets, and only the first packet is returned.
   *         Then the clients will need to call requestResult() to get
   *         additional packets.
   * @throws WsfServiceException
   * 
   * @see org.gusdb.wsf.service.WsfService#requestResult()
   */
  public WsfResponse invoke(String jsonRequest) throws WsfServiceException {
    long start = System.currentTimeMillis();
    try {
      // clean up
      cleanup();

      WsfRequest request = new WsfRequest(jsonRequest);
      String pluginClassName = request.getPluginClass();

      logger.info("Invoking: " + pluginClassName + ", projectId: "
          + request.getProjectId());
      logger.debug("request: " + jsonRequest);

      // use reflection to load the plugin object
      logger.debug("Loading object " + pluginClassName);

      // check if the plugin has been cached
      Plugin plugin;
      if (plugins.containsKey(pluginClassName)) {
        plugin = plugins.get(pluginClassName);
      } else {
        logger.info("Creating plugin " + pluginClassName);
        Class<?> pluginClass = Class.forName(pluginClassName);
        plugin = (Plugin) pluginClass.newInstance();

        // get context
        Map<String, Object> context = loadContext(plugin.getContextKeys());
        plugin.initialize(context);
        plugins.put(pluginClassName, plugin);
      }

      // invoke the plugin
      logger.debug("Invoking Plugin " + pluginClassName);
      WsfResponse result = invokePlugin(plugin, request);
      logger.info("Result Message: '" + result.getMessage() + "'");

      long end = System.currentTimeMillis();
      logger.info("WSF plugin " + pluginClassName + " finished in: " + ((end - start) / 1000.0)
          + " seconds with " + result.getPageCount() + " pages, "
          + result.getResult().length + " results of current page.");

      return result;
    } catch (WsfPluginException | IOException | ClassNotFoundException
        | InstantiationException | IllegalAccessException | JSONException 
        | WsfServiceException ex) {
      logger.error("WSF Service failed.", ex);
      throw new WsfServiceException(ex.getMessage(), ex);
    }
  }

  /**
   * This method is for subsequent calls to get additional packets of a request.
   * 
   * @param invokeId
   * @param pageId
   * @return a string representation of the result for the requested packet.
   * @throws WsfServiceException
   */
  public WsfResponse requestResult(int invokeId, int pageId)
      throws WsfServiceException {
    long start = System.currentTimeMillis();
    logger.debug("Requesting result: invokeId=" + invokeId + ", pageId=" + pageId);
    PluginResponse pluginResponse = new PluginResponse(storageDir, invokeId);
    WsfResponse wsfResponse = new WsfResponse();
    wsfResponse.setInvokeId(invokeId);
    wsfResponse.setCurrentPage(pageId);
    try {
      String[][] results = pluginResponse.getPage(pageId);
      wsfResponse.setResult(results);
      logger.info("Returning result: invokeId=" + invokeId + ", pageId=" + pageId + ", "
          + results.length + " results returned.");
    } catch (WsfPluginException ex) {
      throw new WsfServiceException(ex.getMessage(), ex);
    }
    logAccumulatedTime(start, pageId, pluginResponse);
    return wsfResponse;
  }

  private void logAccumulatedTime(long start, int pageId, PluginResponse pluginResponse) {
    CUMULATIVE_TIME += (System.currentTimeMillis() - start);
    logger.debug("Cumulative processing time in WsfService.requestResult(): " +
        (0.0 + CUMULATIVE_TIME) / 1000 + " (note: not threadsafe)");
    try {
      if (pageId == pluginResponse.getPageCount() - 1)
        CUMULATIVE_TIME = 0; // last page
    }
    catch (WsfPluginException e) {
      /* do nothing */
    }
  }

  /**
   * 
   * Load the objects from context with the given keys.
   * 
   * @param keys
   * @return
   */
  private Map<String, Object> loadContext(String[] keys) {
    Map<String, Object> context = new HashMap<String, Object>();

    ServletContext scontext = null;
    MessageContext msgContext = MessageContext.getCurrentContext();
    if (msgContext != null) {
      Servlet servlet = (Servlet) msgContext.getProperty(HTTPConstants.MC_HTTP_SERVLET);
      scontext = servlet.getServletConfig().getServletContext();
    }
    if (scontext == null)
      scontext = SERVLET_CONTEXT;
    if (scontext != null) {
      // get the configuration path:
      String configPath = scontext.getRealPath(scontext.getInitParameter(Plugin.CTX_CONFIG_PATH));
      context.put(Plugin.CTX_CONFIG_PATH, configPath);
      for (String key : keys) {
        String initValue = scontext.getInitParameter(key);
        context.put(key, initValue);
        Object value = scontext.getAttribute(key);
        context.put(key, value);
      }
    }

    return context;
  }

  private WsfResponse invokePlugin(Plugin plugin, WsfRequest request)
      throws WsfPluginException, WsfServiceException {
    // validate required parameters
    logger.debug("validing required params...");
    validateRequiredParameters(plugin, request);

    // validate columns
    logger.debug("validating columns...");
    validateColumns(plugin, request.getOrderedColumns());

    // validate parameters
    logger.debug("validating params...");
    plugin.validateParameters(request);

    // execute the main function, and obtain result
    logger.debug("getting invoke id...");
    int invokeId = newInvokeId();
    PluginResponse pluginResponse = new PluginResponse(storageDir, invokeId);
    try {
      logger.debug("invoking plugin...");
      plugin.invoke(request, pluginResponse);
      // make sure the results are flushed into storage
      logger.debug("flush plugin response...");
      pluginResponse.flush();
    } catch (WsfPluginException ex) {
      pluginResponse.cleanup();
      throw ex;
    }

    // convert the response
    WsfResponse wsfResponse = new WsfResponse();
    wsfResponse.setInvokeId(pluginResponse.getInvokeId());
    wsfResponse.setMessage(pluginResponse.getMessage());
    wsfResponse.setSignal(pluginResponse.getSignal());
    wsfResponse.setPageCount(pluginResponse.getPageCount());
    wsfResponse.setAttachments(pluginResponse.getAttachments());
    if (pluginResponse.getPageCount() > 0)
      wsfResponse.setResult(pluginResponse.getPage(0));
    return wsfResponse;
  }

  private void validateColumns(Plugin plugin, String[] orderedColumns)
      throws WsfPluginException {
    String[] reqColumns = plugin.getColumns();

    Set<String> colSet = new HashSet<String>();
    for (String col : orderedColumns) {
      colSet.add(col);
    }
    for (String col : reqColumns) {
      if (!colSet.contains(col)) {
        throw new WsfPluginException("The required column is missing: " + col);
      }
    }
    // cross check
    // colSet.clear();
    // colSet = new HashSet<String>(reqColumns.length);
    // for (String col : reqColumns) {
    // colSet.add(col);
    // }
    // for (String col : orderedColumns) {
    // if (!colSet.contains(col)) {
    // throw new WsfServiceException("Unknown column: " + col);
    // }
    // }
  }

  private synchronized int newInvokeId() throws WsfServiceException {
    int invokeId = 0;
    int count = 0;
    while (count < ID_RETRY) {
      // generate a random id, and make sure the id is not being used.
      invokeId = random.nextInt(Integer.MAX_VALUE);
      File file = new File(storageDir, Integer.toString(invokeId));
      if (!file.exists()) {
        if (file.mkdirs())
          break;
      }
      count++;
    }
    if (count >= ID_RETRY)
      throw new WsfServiceException("Cannot create invoke id");
    logger.debug("Generated invoke id: " + invokeId);
    return invokeId;
  }

  private void validateRequiredParameters(Plugin plugin, PluginRequest request)
      throws WsfPluginException {
    String[] reqParams = plugin.getRequiredParameterNames();

    // validate parameters
    Map<String, String> params = request.getParams();
    for (String param : reqParams) {
      if (!params.containsKey(param)) {
        throw new WsfPluginException("The required parameter is missing: "
            + param);
      }
    }
  }

  private void cleanup() throws IOException {
    long now = System.currentTimeMillis() / 1000;
    long elapsed = now - lastCleanup;
    if (elapsed < CLEANUP_INTERVAL)
      return; // to short, no need to clean up

    // start cleanup procedure.
    lastCleanup = now;
    File[] children = storageDir.listFiles();
    for (File child : children) {
      // check if child is old enough
      long time = Files.getLastModifiedTime(child.toPath()).toMillis();
      if ((now - time / 1000) < MAX_CACHE_AGE)
        continue;

      if (!child.isDirectory())
        child.delete();
      else
        PluginResponse.deleteFolder(child);
    }
  }
}
