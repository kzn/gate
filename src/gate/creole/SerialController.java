/*
 *  SerialController.java
 *
 *  Copyright (c) 1995-2012, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Hamish Cunningham, 9/Nov/2000
 *
 *  $Id: SerialController.java 15333 2012-02-07 13:18:33Z ian_roberts $
 */

package gate.creole;

import java.util.*;

import org.apache.log4j.Logger;

import gate.*;
import gate.creole.metadata.*;
import gate.event.*;
import gate.util.*;
import gate.util.profile.Profiler;
import gate.util.Out;

/**
 * Execute a list of PRs serially.
 */
@CreoleResource(name = "Pipeline",
    comment = "A simple serial controller for PR pipelines.",
    helpURL = "http://gate.ac.uk/userguide/sec:developer:apps")
public class SerialController extends AbstractController implements
                                                        CreoleListener,
                                                        CustomDuplication {

  protected static final Logger log = Logger.getLogger(SerialController.class);

  /** Profiler to track PR execute time */
  protected Profiler prof;
  protected HashMap timeMap;
  protected HashMap<String, Long> prTimeMap;

  public SerialController() {
    prList = Collections.synchronizedList(new ArrayList());
    sListener = new InternalStatusListener();
    prTimeMap = new HashMap<String, Long>();

    if(log.isDebugEnabled()) {
      prof = new Profiler();
      prof.enableGCCalling(false);
      prof.printToSystemOut(true);
      timeMap = new HashMap();
    }
    Gate.getCreoleRegister().addCreoleListener(this);
  }

  /**
   * Returns all the {@link gate.ProcessingResource}s contained by thisFeature
   * controller as an unmodifiable list.
   */
  public Collection getPRs() {
    return Collections.unmodifiableList(prList);
  }

  /**
   * Populates this controller from a collection of {@link ProcessingResource}s
   * (optional operation).
   *
   * Controllers that are serializable must implement this method needed by GATE
   * to restore the contents of the controllers.
   *
   * @throws UnsupportedOperationException
   *           if the <tt>setPRs</tt> method is not supported by this
   *           controller.
   */
  public void setPRs(Collection prs) {
    prList.clear();
    Iterator prIter = prs.iterator();
    while(prIter.hasNext())
      add((ProcessingResource)prIter.next());
  }

  public void add(int index, ProcessingResource pr) {
    prList.add(index, pr);
    fireResourceAdded(new ControllerEvent(this, ControllerEvent.RESOURCE_ADDED,
      pr));
  }

  public void add(ProcessingResource pr) {
    prList.add(pr);
    fireResourceAdded(new ControllerEvent(this, ControllerEvent.RESOURCE_ADDED,
      pr));
  }

  public ProcessingResource remove(int index) {
    ProcessingResource aPr = (ProcessingResource)prList.remove(index);
    fireResourceRemoved(new ControllerEvent(this,
      ControllerEvent.RESOURCE_REMOVED, aPr));
    return aPr;
  }

  public boolean remove(ProcessingResource pr) {
    boolean ret = prList.remove(pr);
    if(ret)
      fireResourceRemoved(new ControllerEvent(this,
        ControllerEvent.RESOURCE_REMOVED, pr));
    return ret;
  }

  public ProcessingResource set(int index, ProcessingResource pr) {
    return (ProcessingResource)prList.set(index, pr);
  }

  /**
   * Verifies that all PRs have all their required rutime parameters set.
   */
  protected void checkParameters() throws ExecutionException {
    List badPRs;
    try {
      badPRs = getOffendingPocessingResources();
    }
    catch(ResourceInstantiationException rie) {
      throw new ExecutionException(
        "Could not check runtime parameters for the processing resources:\n"
          + rie.toString());
    }
    if(badPRs != null && !badPRs.isEmpty()) { throw new ExecutionException(
      "Some of the processing resources in this controller have unset "
        + "runtime parameters:\n" + badPRs.toString()); }
  }

  /** Run the Processing Resources in sequence. */
  protected void executeImpl() throws ExecutionException {
    // check all the PRs have the right parameters
    checkParameters();

    if(log.isDebugEnabled()) {
      prof.initRun("Execute controller [" + getName() + "]");
    }

    // execute all PRs in sequence
    interrupted = false;
    for(int i = 0; i < prList.size(); i++) {
      if(isInterrupted()) {

      throw new ExecutionInterruptedException("The execution of the "
        + getName() + " application has been abruptly interrupted!"); }

      runComponent(i);
      if(log.isDebugEnabled()) {
        prof.checkPoint("~Execute PR ["
          + ((ProcessingResource)prList.get(i)).getName() + "]");
        Long timeOfPR =
          (Long)timeMap.get(((ProcessingResource)prList.get(i)).getName());
        if(timeOfPR == null)
          timeMap.put(((ProcessingResource)prList.get(i)).getName(), new Long(
            prof.getLastDuration()));
        else timeMap.put(((ProcessingResource)prList.get(i)).getName(),
          new Long(timeOfPR.longValue() + prof.getLastDuration()));
        log.debug("Time taken so far by "
          + ((ProcessingResource)prList.get(i)).getName() + ": "
          + timeMap.get(((ProcessingResource)prList.get(i)).getName()));

      }
    }

    if(log.isDebugEnabled()) {
      prof.checkPoint("Execute controller [" + getName() + "] finished");
    }
    fireStatusChanged("Finished running " + getName());

  } // executeImpl()

  /**
   * Resets the Time taken by various PRs
   */
  public void resetPrTimeMap() {
    prTimeMap.clear();
  }

  /**
   * Returns the HashMap that lists the total time taken by each PR
   */
  public HashMap<String, Long> getPrTimeMap() {
    return this.prTimeMap;
  }

  /**
   * Executes a {@link ProcessingResource}.
   */
  protected void runComponent(int componentIndex) throws ExecutionException {
    ProcessingResource currentPR =
      (ProcessingResource)prList.get(componentIndex);

    // create the listeners
    FeatureMap listeners = Factory.newFeatureMap();
    listeners.put("gate.event.StatusListener", sListener);
    int componentProgress = 100 / prList.size();
    listeners.put("gate.event.ProgressListener", new IntervalProgressListener(
      componentIndex * componentProgress, (componentIndex + 1)
        * componentProgress));

    // add the listeners
    try {
      AbstractResource.setResourceListeners(currentPR, listeners);
    }
    catch(Exception e) {
      // the listeners setting failed; nothing important
      log.error("Could not set listeners for " + currentPR.getClass().getName()
        + "\n" + e.toString() + "\n...nothing to lose any sleep over.");
    }

    benchmarkFeatures.put(Benchmark.PR_NAME_FEATURE, currentPR.getName());

    long startTime = System.currentTimeMillis();
    // run the thing
    Benchmark.executeWithBenchmarking(currentPR,
            Benchmark.createBenchmarkId(Benchmark.PR_PREFIX + currentPR.getName(),
                    getBenchmarkId()), this, benchmarkFeatures);

    benchmarkFeatures.remove(Benchmark.PR_NAME_FEATURE);

    // calculate the time taken by the PR
    long timeTakenByThePR = System.currentTimeMillis() - startTime;
    Long time = prTimeMap.get(currentPR.getName());
    if(time == null) {
      time = new Long(0);
    }
    time = new Long(time.longValue() + timeTakenByThePR);
    prTimeMap.put(currentPR.getName(), time);


    // remove the listeners
    try {
      AbstractResource.removeResourceListeners(currentPR, listeners);
    }
    catch(Exception e) {
      // the listeners removing failed; nothing important
      log.error("Could not clear listeners for "
        + currentPR.getClass().getName() + "\n" + e.toString()
        + "\n...nothing to lose any sleep over.");
    }
  }// protected void runComponent(int componentIndex)

  /**
   * Cleans the internal data and prepares this object to be collected
   */
  public void cleanup() {
    //stop listening to Creole events.
    Gate.getCreoleRegister().removeCreoleListener(this);
    // close all PRs in this controller, if not members of other apps.
    if(prList != null && !prList.isEmpty()) {
      try {
        //get all the other controllers
        List<Resource> allOtherControllers  = 
          Gate.getCreoleRegister().getAllInstances("gate.Controller");
        allOtherControllers.remove(this);
        //get all the PRs in all the other controllers
        List<Resource> prsInOtherControllers = new ArrayList<Resource>();
        for(Resource aController : allOtherControllers){
          prsInOtherControllers.addAll(((Controller)aController).getPRs());
        }
        //remove all PRs in this controller, that are not also in other 
        //controllers
//        for(Iterator prIter = getPRs().iterator(); prIter.hasNext();){
//          ProcessingResource aPr = (ProcessingResource)prIter.next();
//          if(!prsInOtherControllers.contains(aPr)){
//            prIter.remove();
//            Factory.deleteResource((Resource)aPr);
//          }
//        }
        for(Object aPr : new ArrayList(getPRs())){
          if(!prsInOtherControllers.contains(aPr)){
            Factory.deleteResource((Resource)aPr);
          }
        }
      }
      catch(GateException e) {
        //ignore
      }
    }
  }
  
  /**
   * Duplicate this controller.  We perform a default duplication of
   * the controller itself, then recursively duplicate its contained
   * PRs and add these duplicates to the copy.
   */
  public Resource duplicate(Factory.DuplicationContext ctx)
          throws ResourceInstantiationException {
    // duplicate this controller in the default way - this handles
    // subclasses nicely
    Controller c = (Controller)Factory.defaultDuplicate(this, ctx);
    
    // duplicate each of our PRs
    List<ProcessingResource> newPRs = new ArrayList<ProcessingResource>();
    for(Object pr : prList) {
      newPRs.add((ProcessingResource)Factory.duplicate((Resource)pr, ctx));
    }
    // and set this duplicated list as the PRs of the copy
    c.setPRs(newPRs);
    
    return c;
  }

  /** The list of contained PRs */
  protected List prList;

  /** A proxy for status events */
  protected StatusListener sListener;

  public void resourceLoaded(CreoleEvent e) {
  }

  public void resourceUnloaded(CreoleEvent e) {
    // remove all occurences of the resource from this controller
    if(e.getResource() instanceof ProcessingResource)
    // while(prList.remove(e.getResource()));
      // remove all occurrences of this PR from the controller
      // Use the controller's remove method (rather than prList's so that
      // subclasses of this controller type can add their own functionality
      while(remove((ProcessingResource)e.getResource()))
        ;
    // remove links in parameters
    for(int i = 0; i < prList.size(); i++) {
      ProcessingResource aPr = (ProcessingResource)prList.get(i);
      ResourceData rData =
        (ResourceData)Gate.getCreoleRegister().get(aPr.getClass().getName());
      if(rData != null) {
        Iterator rtParamDisjIter =
          rData.getParameterList().getRuntimeParameters().iterator();
        while(rtParamDisjIter.hasNext()) {
          List aDisjunction = (List)rtParamDisjIter.next();
          Iterator rtParamIter = aDisjunction.iterator();
          while(rtParamIter.hasNext()) {
            Parameter aParam = (Parameter)rtParamIter.next();
            String paramName = aParam.getName();
            try {
              if(aPr.getParameterValue(paramName) == e.getResource()) {
                aPr.setParameterValue(paramName, null);
              }
            }
            catch(ResourceInstantiationException rie) {
              // nothing to do - this should never happen
              throw new GateRuntimeException(rie);
            }
          }
        }
      }
    }
  }

  public void resourceRenamed(Resource resource, String oldName, String newName) {
  }

  public void datastoreOpened(CreoleEvent e) {
  }

  public void datastoreCreated(CreoleEvent e) {
  }

  public void datastoreClosed(CreoleEvent e) {
  }

} // class SerialController
