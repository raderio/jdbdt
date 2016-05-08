package org.jdbdt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jdbdt.CallInfo.MethodInfo;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * JDBDT log.
 * 
 * @since 0.1
 *
 */
final class Log {
  /**
   * Output stream.
   */
  private final PrintStream out;
  /**
   * Closing behavior flag.
   */
  private final boolean ignoreClose;
  /**
   * XML document instance.
   */
  private final Document xmlDoc;

  /**
   * Construct a log with an associated output file.
   * @param ouputFile Output file.
   * @throws FileNotFoundException If the file cannot be opened/created. 
   */
  Log(File ouputFile) throws FileNotFoundException {
    this(new PrintStream(new FileOutputStream(ouputFile)), false);
  }
  /**
   * Construct a log with an associated output stream.
   * @param out Output stream.
   */
  Log(PrintStream out) {
    this(out, true);
  }
  
  /**
   * General constructor.
   * @param out Output stream.
   * @param ignoreClose If true, calls to {@link #close()} on the
   * created log will not close the output stream.
   */
  private Log(PrintStream out, boolean ignoreClose) {
    this.out = out;
    this.ignoreClose = ignoreClose;
    this.xmlDoc = XML_DOC_FACTORY.newDocument(); 
  }

  @SuppressWarnings({ "javadoc" })
  private Element root() {
    Element e = createNode(null, ROOT_TAG);
    e.setAttribute(VERSION_TAG, JDBDT.version());
    e.setAttribute(TIME_TAG, new Timestamp(System.currentTimeMillis()).toString());
    return e;
  }

  @SuppressWarnings("javadoc")
  private void flush(Element rootNode) {
    DOMSource ds = new DOMSource(rootNode);
    try {
      StreamResult sr = new StreamResult(out);
      XML_TRANSFORMER.transform(ds, sr);
      out.flush();
    } 
    catch (Throwable e) {
      throw new InternalAPIError(e);
    } 
  }

  /**
   * Close the log.
   * 
   * <p>
   * The log should not be used after this method is called.
   * </p>
   * 
   */
  void close() {
    if (!ignoreClose) {
      out.close();
    }
  }
  /**
   * Write a data set to the log.
   * @param callInfo Call info.
   * @param data Data set.
   */
  void write(CallInfo callInfo, DataSet data) {
    Element rootNode = root();
    write(rootNode, callInfo);
    Element dsNode = createNode(rootNode, DATA_SET_TAG);
    write(dsNode, data.getSource().getMetaData());
    write(dsNode, ROWS_TAG, data.getSource().getMetaData().columns(), data.getRows().iterator());
    flush(rootNode);
  }

  @SuppressWarnings("javadoc")
  private void write(Element root, CallInfo info) {
    Element ctxNode = createNode(root, CONTEXT_TAG);
    if (info.getMessage().length() > 0) {
      simpleNode(ctxNode, CTX_MESSAGE_TAG, info.getMessage());
    }
    write(ctxNode, CTX_CALLER_TAG, info.getCallerMethodInfo());
    write(ctxNode, CTX_API_METHOD_TAG, info.getAPIMethodInfo());
  }
  
  @SuppressWarnings("javadoc")
  private void write
  (Element ctxNode, String tag, MethodInfo mi) {
     Element miNode = createNode(ctxNode, tag);
     simpleNode(miNode, CTX_CLASS_TAG, mi.getClassName());
     simpleNode(miNode, CTX_METHOD_TAG, mi.getMethodName());
     simpleNode(miNode, CTX_FILE_TAG, mi.getFileName());
     simpleNode(miNode, CTX_LINE_TAG, String.valueOf(mi.getLineNumber()));
  }
  
  @SuppressWarnings("javadoc")
  private void write(Element parent, MetaData md) {
    int index = 1;
    Element node = createNode(parent, COLUMNS_TAG);
    node.setAttribute(COUNT_TAG, String.valueOf(md.getColumnCount()));
    for (MetaData.ColumnInfo col : md.columns()) {
      Element colNode = createNode(node, COLUMN_TAG);
      colNode.setAttribute(INDEX_TAG, String.valueOf(index++));
      colNode.setAttribute(LABEL_TAG, col.label());
      colNode.setAttribute(SQL_TYPE_TAG, col.type().toString());
    } 
  }
  
  /**
   * Log SQL code.
   * @param sql SQL code.
   */
  void writeSQL(String sql) {
    Element rootNode = root(),
            sqlNode = createNode(rootNode, SQL_TAG);
    sqlNode.appendChild(xmlDoc.createCDATASection(sql));
    flush(rootNode);
  }
  
  /**
   * Log delta assertion.
   * @param callInfo Call info.
   * @param da Delta assertion.
   */
  void write(CallInfo callInfo, DeltaAssertion da) {
    final Element rootNode = root();
            
    final MetaData md = da.getMetaData();
    final List<MetaData.ColumnInfo> mdCols = md.columns();
    write(rootNode, callInfo);
    final Element daNode = createNode(rootNode, DELTA_ASSERTION_TAG);
    write(daNode, md);
    final Element expectedNode = createNode(daNode, EXPECTED_TAG);    
    write(expectedNode, 
          OLD_DATA_TAG, 
          mdCols,
          da.data(DeltaAssertion.IteratorType.OLD_DATA_EXPECTED));
    write(expectedNode, 
          NEW_DATA_TAG, 
          mdCols,
          da.data(DeltaAssertion.IteratorType.NEW_DATA_EXPECTED));
    if (! da.passed()) {
      Element errorsNode = createNode(daNode, ERRORS_TAG),
              oldDataErrors = createNode(errorsNode, OLD_DATA_TAG),
              newDataErrors = createNode(errorsNode, NEW_DATA_TAG);
      write(oldDataErrors, 
            EXPECTED_TAG, 
            mdCols,
            da.data(DeltaAssertion.IteratorType.OLD_DATA_ERRORS_EXPECTED));
      write(oldDataErrors, 
            ACTUAL_TAG, 
            mdCols,
            da.data(DeltaAssertion.IteratorType.OLD_DATA_ERRORS_ACTUAL));
      write(newDataErrors, 
          EXPECTED_TAG, 
          mdCols,
          da.data(DeltaAssertion.IteratorType.NEW_DATA_ERRORS_EXPECTED));
      write(newDataErrors, 
            ACTUAL_TAG, 
            mdCols,
            da.data(DeltaAssertion.IteratorType.NEW_DATA_ERRORS_ACTUAL));
    }
    flush(rootNode);
  }

  /**
   * Log state assertion.
   * @param callInfo Call info.
   * @param sa State assertion.
   */
  void write(CallInfo callInfo, StateAssertion sa) {
    final Element rootNode = root(); 
    final MetaData md = sa.getMetaData();
    final List<MetaData.ColumnInfo> mdCols = md.columns();
    write(rootNode, callInfo);
    final Element saNode = createNode(rootNode, STATE_ASSERTION_TAG);
    write(saNode, md);
    write(saNode, 
          EXPECTED_TAG, 
          mdCols,
          sa.data(StateAssertion.IteratorType.EXPECTED_DATA));
    if (! sa.passed()) {
      Element errorsNode = createNode(saNode, ERRORS_TAG);
      write(errorsNode, 
            EXPECTED_TAG, 
            mdCols,
            sa.data(StateAssertion.IteratorType.ERRORS_EXPECTED));
      write(errorsNode, 
            ACTUAL_TAG, 
            mdCols,
            sa.data(StateAssertion.IteratorType.ERRORS_ACTUAL));
    }
    flush(rootNode);
  }
  @SuppressWarnings("javadoc")
  private void write(Element parent, String tag, List<MetaData.ColumnInfo> columns, Iterator<Row> itr) {
    int size = 0;
    Element topNode = createNode(parent, tag);
    while (itr.hasNext()) {
      Row r = itr.next();
      Object[] data = r.data();
      Element rowElem = createNode(topNode, ROW_TAG);
      for (int i=0; i < data.length; i++) {
        Element colNode = createNode(rowElem, COLUMN_TAG);
        colNode.setAttribute(LABEL_TAG, columns.get(i).label());
        if (data[i] != null) {
          colNode.setAttribute(JAVA_TYPE_TAG, data[i].getClass().getName());
          colNode.setTextContent(data[i].toString());
        } else {
          colNode.setTextContent(NULL_VALUE);
        }
      }
      size++;
    }
    topNode.setAttribute(COUNT_TAG,  String.valueOf(size));
  }
  
  @SuppressWarnings("javadoc")
  private Element createNode(Element parent, String tag) {
    Element node = xmlDoc.createElement(tag);
    if (parent != null) {
      parent.appendChild(node);
    }
    return node;
  }

  @SuppressWarnings("javadoc")
  private void simpleNode(Element parent, String tag, String value) {
    Element child = createNode(parent, tag);
    child.setTextContent(value);
  }
  
  /**
   * Document builder factory handle.
   */
  private static final DocumentBuilder XML_DOC_FACTORY;

  /**
   * Transformer handle.
   */
  private static final Transformer XML_TRANSFORMER;

  @SuppressWarnings("javadoc")
  private static final String ROOT_TAG = "jdbdt-log-message";
  @SuppressWarnings("javadoc")
  private static final String VERSION_TAG = "version";
  @SuppressWarnings("javadoc")
  private static final String TIME_TAG = "time";
  @SuppressWarnings("javadoc")
  private static final String DELTA_ASSERTION_TAG = "delta-assertion";
  @SuppressWarnings("javadoc")
  private static final String STATE_ASSERTION_TAG = "state-assertion";
  @SuppressWarnings("javadoc")
  private static final String ERRORS_TAG = "errors";
  @SuppressWarnings("javadoc")
  private static final String EXPECTED_TAG = "expected";
  @SuppressWarnings("javadoc")
  private static final String ACTUAL_TAG = "actual";
  @SuppressWarnings("javadoc")
  private static final String DATA_SET_TAG = "data-set";
  @SuppressWarnings("javadoc")
  private static final String COLUMNS_TAG = "columns";
  @SuppressWarnings("javadoc")
  private static final String OLD_DATA_TAG = "old-data";
  @SuppressWarnings("javadoc")
  private static final String NEW_DATA_TAG = "new-data";
  @SuppressWarnings("javadoc")
  private static final String ROWS_TAG = "rows";
  @SuppressWarnings("javadoc")
  private static final String ROW_TAG = "row";
  @SuppressWarnings("javadoc")
  private static final String SQL_TAG = "sql";
  @SuppressWarnings("javadoc")
  private static final String COLUMN_TAG = "column";
  @SuppressWarnings("javadoc")
  private static final String LABEL_TAG = "label";
  @SuppressWarnings("javadoc")
  private static final String SQL_TYPE_TAG = "sql-type";
  @SuppressWarnings("javadoc")
  private static final String JAVA_TYPE_TAG = "java-type";
  @SuppressWarnings("javadoc")
  private static final String INDEX_TAG = "index";
  @SuppressWarnings("javadoc")
  private static final String COUNT_TAG = "count";
  @SuppressWarnings("javadoc")
  private static final String NULL_VALUE = "NULL";
  @SuppressWarnings("javadoc")
  private static final String CONTEXT_TAG = "context";
  @SuppressWarnings("javadoc")
  private static final String CTX_CALLER_TAG = "caller";
  @SuppressWarnings("javadoc")
  private static final String CTX_API_METHOD_TAG = "api-method";
  @SuppressWarnings("javadoc")
  private static final String CTX_LINE_TAG = "line";
  @SuppressWarnings("javadoc")
  private static final String CTX_FILE_TAG = "file";
  @SuppressWarnings("javadoc")
  private static final String CTX_CLASS_TAG = "class";
  @SuppressWarnings("javadoc")
  private static final String CTX_MESSAGE_TAG = "message";
  @SuppressWarnings("javadoc")
  private static final String CTX_METHOD_TAG = "method";
  static {
    try {
      XML_DOC_FACTORY = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      XML_TRANSFORMER = TransformerFactory.newInstance().newTransformer();
      XML_TRANSFORMER.setOutputProperty(OutputKeys.INDENT, "yes");
      XML_TRANSFORMER.setOutputProperty(OutputKeys.METHOD, "xml");
      XML_TRANSFORMER.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      XML_TRANSFORMER.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      XML_TRANSFORMER.setOutputProperty(OutputKeys.STANDALONE, "yes");
      XML_TRANSFORMER.setOutputProperty(OutputKeys.VERSION, "1.0");
    } catch (ParserConfigurationException | TransformerConfigurationException
        | TransformerFactoryConfigurationError e) {
      throw new InternalAPIError(e);
    }
  }



  
}
