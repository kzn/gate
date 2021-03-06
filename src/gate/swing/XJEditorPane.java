package gate.swing;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedList;
import java.util.Vector;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;

import gate.event.StatusListener;
import gate.gui.MainFrame;
import gate.util.Err;

/**
 * An enhanced version of {@link javax.swing.JEditorPane} that is able of
 * handling hyperlinks from the HTML document displayed.
 */
public class XJEditorPane extends JEditorPane {

  public XJEditorPane(){
    super();
    init();
  }

  public XJEditorPane(String url) throws IOException{
    super(url);
    init();
  }

  public XJEditorPane(URL initialPage)throws IOException{
    super(initialPage);
    init();
  }

  protected void init(){
    initLocalData();
    initListeners();
  }//protected void init()

  protected void initLocalData(){
    backUrls = new LinkedList();
    forwardUrls = new LinkedList();
    try{
      backAction = new BackAction();
      forwardAction = new ForwardAction();
    }catch(IOException ioe){
      Err.prln("Resource mising! Is your classpath OK?");
      ioe.printStackTrace(Err.getPrintWriter());
    }
  }//protected void initLocalData()

  protected void initListeners(){
    addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e){
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED){
          if (e instanceof HTMLFrameHyperlinkEvent) {
              HTMLFrameHyperlinkEvent  evt = (HTMLFrameHyperlinkEvent)e;
              HTMLDocument doc = (HTMLDocument)getDocument();
              doc.processHTMLFrameHyperlinkEvent(evt);
          }else{
            try {
              backUrls.addLast(getPage());
              forwardUrls.clear();
              setPage(e.getURL().toExternalForm());
            }catch (Throwable t){
              t.printStackTrace(Err.getPrintWriter());
            }
          }
        }else if(e.getEventType() == HyperlinkEvent.EventType.ENTERED){
          fireStatusChanged(e.getURL().toExternalForm());
        }else if(e.getEventType() == HyperlinkEvent.EventType.EXITED){
          fireStatusChanged("");
        }
      }//public void hyperlinkUpdate(HyperlinkEvent e)
    });
  }//protected void initListeners()

  public Action getForwardAction(){
    return forwardAction;
  }

  public Action getBackAction(){
    return backAction;
  }

  public void setPage(URL page) throws IOException{
    try{
      super.setPage(page);
    }catch(Exception e){
      fireStatusChanged(e.toString());
      e.printStackTrace(Err.getPrintWriter());
    }
    updateEnableState();
  }

  class ForwardAction extends AbstractAction{
    private ForwardAction() throws IOException{
      super("Forward", MainFrame.getIcon("forward"));
    }

    public void actionPerformed(ActionEvent e){
      backUrls.addLast(getPage());
      try{
        setPage((URL)forwardUrls.removeFirst());
      }catch(IOException ioe){
        ioe.printStackTrace(Err.getPrintWriter());
      }
    }
  }//class ForwardAction extends AbstractAction

  class BackAction extends AbstractAction{
    private BackAction() throws IOException{
      super("Back", MainFrame.getIcon("back"));
    }

    public void actionPerformed(ActionEvent e){
      forwardUrls.addFirst(getPage());
      try{
        setPage((URL)backUrls.removeLast());
      }catch(IOException ioe){
        ioe.printStackTrace(Err.getPrintWriter());
      }
    }
  }//class ForwardAction extends AbstractAction


  /**
   * Updates the enabled/disabled state for the back/forward actions
   */
  protected void updateEnableState(){
    forwardAction.setEnabled(!forwardUrls.isEmpty());
    backAction.setEnabled(!backUrls.isEmpty());
  }
  public synchronized void removeStatusListener(StatusListener l) {
    if (statusListeners != null && statusListeners.contains(l)) {
      Vector v = (Vector) statusListeners.clone();
      v.removeElement(l);
      statusListeners = v;
    }
  }
  public synchronized void addStatusListener(StatusListener l) {
    Vector v = statusListeners == null ? new Vector(2) : (Vector) statusListeners.clone();
    if (!v.contains(l)) {
      v.addElement(l);
      statusListeners = v;
    }
  }

  protected LinkedList backUrls;
  protected LinkedList forwardUrls;
  protected Action backAction;
  protected Action forwardAction;
  private transient Vector statusListeners;
  protected void fireStatusChanged(String e) {
    if (statusListeners != null) {
      Vector listeners = statusListeners;
      int count = listeners.size();
      for (int i = 0; i < count; i++) {
        ((StatusListener) listeners.elementAt(i)).statusChanged(e);
      }
    }
  }
}//public class XJEditorPane extends JEditorPane
