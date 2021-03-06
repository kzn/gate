/*
 *  Copyright (c) 1995-2012, The University of Sheffield. See the file
 *  COPYRIGHT.txt in the software or at http://gate.ac.uk/gate/COPYRIGHT.txt
 *
 *  This file is part of GATE (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Library General Public License,
 *  Version 2, June 1991 (in the distribution as file licence.html,
 *  and also available at http://gate.ac.uk/gate/licence.html).
 *
 *  Valentin Tablan 26/10/2001
 *
 *  $Id: PRPersistence.java 15333 2012-02-07 13:18:33Z ian_roberts $
 *
 */
package gate.util.persistence;

import java.util.*;

import gate.*;
import gate.creole.*;
import gate.persist.PersistenceException;


public class PRPersistence extends ResourcePersistence {
  /**
   * Populates this Persistence with the data that needs to be stored from the
   * original source object.
   */
  public void extractDataFromSource(Object source)throws PersistenceException{
    if(! (source instanceof ProcessingResource)){
      throw new UnsupportedOperationException(
                getClass().getName() + " can only be used for " +
                ProcessingResource.class.getName() +
                " objects!\n" + source.getClass().getName() +
                " is not a " + ProcessingResource.class.getName());
    }

    super.extractDataFromSource(source);
    Resource res = (Resource)source;

    ResourceData rData = (ResourceData)
                         Gate.getCreoleRegister().get(res.getClass().getName());
    if(rData == null) throw new PersistenceException(
                                "Could not find CREOLE data for " +
                                res.getClass().getName());

    //now get the runtime params
    ParameterList params = rData.getParameterList();
    try{
      //get the values for the init time parameters
      runtimeParams = Factory.newFeatureMap();
      //this is a list of lists
      Iterator parDisjIter = ((List)params.getRuntimeParameters()).iterator();
      while(parDisjIter.hasNext()){
        Iterator parIter = ((List)parDisjIter.next()).iterator();
        while(parIter.hasNext()){
          Parameter parameter = (Parameter)parIter.next();
          String parName = parameter.getName();
          Object parValue = res.getParameterValue(parName);
          ((Map)runtimeParams).put(parName,parValue);
        }
      }
      runtimeParams = PersistenceManager.
                      getPersistentRepresentation(runtimeParams);
    }catch(ResourceInstantiationException rie){
      throw new PersistenceException(rie);
    }
  }

  /**
   * Creates a new object from the data contained. This new object is supposed
   * to be a copy for the original object used as source for data extraction.
   */
  public Object createObject()throws PersistenceException,
                                     ResourceInstantiationException{
    Object res = super.createObject();
    //now add the runtime parameters
    if(runtimeParams != null){
      runtimeParams = PersistenceManager.
                      getTransientRepresentation(runtimeParams);
      ((Resource)res).setParameterValues((FeatureMap)runtimeParams);
    }

    return res;
  }

  protected Object runtimeParams;
  static final long serialVersionUID = 2031381604712340552L;
}