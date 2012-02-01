package org.exoplatform.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.exoplatform.R;
import org.exoplatform.model.ExoFile;
import org.exoplatform.singleton.AccountSetting;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ExoDocumentUtils {

  public static String repositoryHomeURL = null;

  public static boolean putFileToServerFromLocal(String url, File fileManager, String fileType) {
    File tempFile = PhotoUtils.reziseFileImage(fileManager);
    HttpResponse response = null;
    try {

      HttpPut put = new HttpPut(url);
      FileEntity fileEntity = new FileEntity(tempFile, fileType);
      put.setEntity(fileEntity);
      fileEntity.setContentType(fileType);

      response = ExoConnectionUtils.httpClient.execute(put);
      int status = response.getStatusLine().getStatusCode();
      if (status >= 200 && status < 300) {
        return true;
      } else
        return false;

    } catch (IOException e) {
      return false;
    } finally {
      tempFile.delete();
    }

  }

  public static boolean setRepositoryHomeUrl(String userName, String domain) {

    if (repositoryHomeURL == null) {
      StringBuffer buffer = new StringBuffer();
      buffer.append(domain);
      buffer.append(ExoConstants.DOCUMENT_PATH);

      int length = userName.length();
      if (length < 4) {
        for (int i = 1; i < length; i++) {
          String userNameLevel = userName.substring(0, i);
          buffer.append("/");
          buffer.append(userNameLevel);
          buffer.append("___");
        }
      } else {
        for (int i = 1; i < 4; i++) {
          String userNameLevel = userName.substring(0, i);
          buffer.append("/");
          buffer.append(userNameLevel);
          buffer.append("___");
        }
      }

      buffer.append("/");
      buffer.append(userName);

      try {

        WebdavMethod copy = new WebdavMethod("HEAD", buffer.toString());
        int status = ExoConnectionUtils.httpClient.execute(copy).getStatusLine().getStatusCode();

        if (status >= 200 && status < 300)
          repositoryHomeURL = buffer.toString();
        else
          repositoryHomeURL = domain + ExoConstants.DOCUMENT_PATH + "/" + userName;
        return true;
      } catch (IOException e) {

        repositoryHomeURL = null;
        return false;
      }

    }
    return true;
    // Log.e("123: ", repositoryHomeURL);

  }

  // Get file array from URL
  public static ArrayList<ExoFile> getPersonalDriveContent(ExoFile file) {

    ArrayList<ExoFile> arrFilesTmp = new ArrayList<ExoFile>();

    if (file == null) {
      arrFilesTmp.add(null);
      arrFilesTmp.addAll(getDrives("personal"));
      arrFilesTmp.add(null);
      arrFilesTmp.addAll(getDrives("group"));
    } else {
      arrFilesTmp.addAll(getContentOfFolder(file));
    }

    return arrFilesTmp;
  }

  public static String fullURLofFile(String url) {
    String domain = AccountSetting.getInstance().getDomainName();
    return domain + ExoConstants.DOCUMENT_JCR_PATH_REST + url;

  }

  public static ArrayList<ExoFile> getDrives(String driveName) {

    String domain = AccountSetting.getInstance().getDomainName();
    String urlStr = domain + ExoConstants.DOCUMENT_DRIVE_PATH_REST + driveName;
    // Initialize the blogEntries MutableArray that we declared in the header
    ArrayList<ExoFile> folderArray = new ArrayList<ExoFile>();

    Document obj_doc = null;
    DocumentBuilderFactory doc_build_fact = null;
    DocumentBuilder doc_builder = null;
    try {
      doc_build_fact = DocumentBuilderFactory.newInstance();
      doc_builder = doc_build_fact.newDocumentBuilder();
      InputStream is = ExoConnectionUtils.sendRequest(urlStr);
      obj_doc = doc_builder.parse(is);

      NodeList obj_nod_list = null;
      if (null != obj_doc) {
        org.w3c.dom.Element feed = obj_doc.getDocumentElement();
        obj_nod_list = feed.getElementsByTagName("Folder");

        for (int i = 0; i < obj_nod_list.getLength(); i++) {
          Node itemNode = obj_nod_list.item(i);
          if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
            Element itemElement = (Element) itemNode;

            ExoFile file = new ExoFile();

            file.name = itemElement.getAttribute("name");
            file.workspaceName = itemElement.getAttribute("workspaceName");
            file.driveName = file.name;
            file.currentFolder = itemElement.getAttribute("currentFolder");
            if (file.currentFolder == null)
              file.currentFolder = "";
            file.isFolder = true;

            folderArray.add(file);
          }
        }
      }

    } catch (ParserConfigurationException e) {
      folderArray = null;
    } catch (SAXException e) {
      folderArray = null;
    } catch (IOException e) {
      folderArray = null;
    }

    return folderArray;
  }

  public static ArrayList<ExoFile> getContentOfFolder(ExoFile file) {

    String domain = AccountSetting.getInstance().getDomainName();
    String urlStr = domain + ExoConstants.DOCUMENT_FILE_PATH_REST + file.driveName
        + ExoConstants.DOCUMENT_WORKSPACE_NAME + file.workspaceName
        + ExoConstants.DOCUMENT_CURRENT_FOLDER + file.currentFolder;

    urlStr = URLAnalyzer.encodeUrl(urlStr);

    // Initialize the blogEntries MutableArray that we declared in the header
    ArrayList<ExoFile> folderArray = new ArrayList<ExoFile>();

    Document obj_doc = null;
    DocumentBuilderFactory doc_build_fact = null;
    DocumentBuilder doc_builder = null;
    try {
      doc_build_fact = DocumentBuilderFactory.newInstance();
      doc_builder = doc_build_fact.newDocumentBuilder();
      InputStream is = ExoConnectionUtils.sendRequest(urlStr);
      obj_doc = doc_builder.parse(is);

      NodeList obj_nod_list = null;
      if (null != obj_doc) {
        org.w3c.dom.Element feed = obj_doc.getDocumentElement();

        // Get folders
        obj_nod_list = feed.getElementsByTagName("Folder");

        for (int i = 0; i < obj_nod_list.getLength(); i++) {
          Node itemNode = obj_nod_list.item(i);
          if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
            Element itemElement = (Element) itemNode;
            if (i > 0) {
              ExoFile newFile = new ExoFile();
              newFile.name = itemElement.getAttribute("name");
              newFile.path = fullURLofFile(itemElement.getAttribute("path"));
              newFile.workspaceName = itemElement.getAttribute("workspaceName");
              newFile.driveName = itemElement.getAttribute("driveName");
              newFile.currentFolder = itemElement.getAttribute("currentFolder");
              if (newFile.currentFolder == null)
                newFile.currentFolder = "";
              newFile.isFolder = true;

              folderArray.add(newFile);
            }

          }
        }

        // Get files
        obj_nod_list = feed.getElementsByTagName("File");

        for (int i = 0; i < obj_nod_list.getLength(); i++) {
          Node itemNode = obj_nod_list.item(i);
          if (itemNode.getNodeType() == Node.ELEMENT_NODE) {
            Element itemElement = (Element) itemNode;

            ExoFile newFile = new ExoFile();
            newFile.path = fullURLofFile(itemElement.getAttribute("path"));
            newFile.name = itemElement.getAttribute("name");
            newFile.workspaceName = itemElement.getAttribute("workspaceName");
            newFile.driveName = file.name;
            newFile.currentFolder = itemElement.getAttribute("currentFolder");
            newFile.nodeType = itemElement.getAttribute("nodeType");
            newFile.isFolder = false;

            folderArray.add(newFile);
          }
        }

      }

    } catch (ParserConfigurationException e) {
      return null;
    } catch (SAXException e) {
      return null;
    } catch (IOException e) {
      return null;
    }

    return folderArray;

  }

  // Get file/folder icon file name form content type
  static public String getFileFolderIconName(String contentType) {
    String strIconFileName = "documenticonforunknown";
    if (contentType != null) {
      if (contentType.indexOf("image") >= 0)
        strIconFileName = "documenticonforimage";
      else if (contentType.indexOf("video") >= 0)
        strIconFileName = "documenticonforvideo";
      else if (contentType.indexOf("audio") >= 0)
        strIconFileName = "documenticonformusic";
      else if (contentType.indexOf("application/msword") >= 0)
        strIconFileName = "documenticonforword";
      else if (contentType.indexOf("application/pdf") >= 0)
        strIconFileName = "documenticonforpdf";
      else if (contentType.indexOf("application/xls") >= 0)
        strIconFileName = "documenticonforxls";
      else if (contentType.indexOf("application/vnd.ms-powerpoint") >= 0)
        strIconFileName = "documenticonforppt";
      else if (contentType.indexOf("text") >= 0)
        strIconFileName = "documenticonfortxt";
    } else
      strIconFileName = "documenticonforunknown";

    return strIconFileName;
  }

  public static int getPicIDFromName(String name) {
    int id = 0;
    if (name != null) {
      if (name.equalsIgnoreCase("documenticonforimage"))
        id = R.drawable.documenticonforimage;
      else if (name.equalsIgnoreCase("documenticonforvideo"))
        id = R.drawable.documenticonforvideo;
      else if (name.equalsIgnoreCase("documenticonformusic"))
        id = R.drawable.documenticonformusic;
      else if (name.equalsIgnoreCase("documenticonforword"))
        id = R.drawable.documenticonforword;
      else if (name.equalsIgnoreCase("documenticonforpdf"))
        id = R.drawable.documenticonforpdf;
      else if (name.equalsIgnoreCase("documenticonforxls"))
        id = R.drawable.documenticonforxls;
      else if (name.equalsIgnoreCase("documenticonforppt"))
        id = R.drawable.documenticonforppt;
      else if (name.equalsIgnoreCase("documenticonfortxt"))
        id = R.drawable.documenticonfortxt;
      else
        id = R.drawable.documenticonforunknown;
    } else
      id = R.drawable.documenticonforunknown;

    return id;

  }

  public static String getParentUrl(String url) {

    int index = url.lastIndexOf("/");
    if (index > 0)
      return url.substring(0, index);

    return "";
  }

  public static String getLastPathComponent(String url) {

    int index = url.lastIndexOf("/");
    if (index > 0)
      return url.substring(url.lastIndexOf("/") + 1, url.length());

    return url;

  }

  public static boolean isContainSpecialChar(String str, String charSet) {

    // try {
    Pattern patt = Pattern.compile(charSet);
    Matcher matcher = patt.matcher(str);
    return matcher.find();
    // } catch (RuntimeException e) {
    // return false;
    // }
  }
}
