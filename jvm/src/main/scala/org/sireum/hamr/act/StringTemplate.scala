// #Sireum

package org.sireum.hamr.act

import org.sireum._
import org.sireum.hamr.ir
import org.sireum.hamr.ir.Component

object StringTemplate {

  val SB_VERIFY: String = Util.cbrand("VERIFY")

  val MON_READ_ACCESS: String = Util.cbrand("MONITOR_READ_ACCESS")
  val MON_WRITE_ACCESS: String = Util.cbrand("MONITOR_WRITE_ACCESS")

  val SeqNumName: String = "seqNum"
  val SeqNumType: String = s"${SeqNumName}_t"
  
  def tbInterface(macroName: String): ST = {
    val r : ST = st"""#ifdef ${macroName}
                     |#define ${macroName}
                     |
                     |#endif // ${macroName}
                     |"""
    return r
  }

  def tbTypeHeaderFile(macroName: String, typeHeaderFileName: String, defs: ISZ[ST], preventBadging: B): ST = {
    val badges: ST = if(preventBadging) {st""} else {st"""
                                                         |#define $MON_READ_ACCESS 111
                                                         |#define $MON_WRITE_ACCESS 222"""}
    val macroname = s"__${Util.cbrand("AADL")}_${typeHeaderFileName}__H"

    val body = st"""#ifndef ${macroname}
                   |#define ${macroname}
                   |
                   |#include <stdbool.h>
                   |#include <stdint.h>
                   |
                   |#ifndef ${SB_VERIFY}
                   |#include <stddef.h>
                   |#endif // ${SB_VERIFY}
                   |
                   |#define __${Util.cbrand("OS")}_CAMKES__${badges}
                   |
                   |#ifndef ${SB_VERIFY}
                   |#define MUTEXOP(OP)\
                   |if((OP) != 0) {\
                   |  fprintf(stderr,"Operation " #OP " failed in %s at %d.\n",__FILE__,__LINE__);\
                   |  *((int*)0)=0xdeadbeef;\
                   |}
                   |#else
                   |#define MUTEXOP(OP) OP
                   |#endif // ${SB_VERIFY}
                   |#ifndef ${SB_VERIFY}
                   |#define CALLBACKOP(OP)\
                   |if((OP) != 0) {\
                   |  fprintf(stderr,"Operation " #OP " failed in %s at %d.\n",__FILE__,__LINE__);\
                   |  *((int*)0)=0xdeadbeef;\
                   |}
                   |#else
                   |#define CALLBACKOP(OP) OP
                   |#endif // ${SB_VERIFY}
                   |
                   |${(defs, "\n\n")}
                   |
                   |#endif // ${macroname}
                   |"""
    return body
  }

  def tbMissingType() : ST = {
    return st"""// placeholder for unspecified types in the AADL model
               |typedef bool ${Util.MISSING_AADL_TYPE};"""
  }

  val receivedDataVar: String = "receivedData"
  
  def tbMonReadWrite(typeName: String, dim: Z, monitorTypeHeaderFilename: String, typeHeaderFilename: String,
                     preventBadging: B): ST = {
    val read: ST = st"""*m = contents;
                       |return ${receivedDataVar};"""
    
    val mon_read: ST = if(preventBadging) { 
      read 
    } else {
      st"""if (mon_get_sender_id() != $MON_READ_ACCESS) {
          |  return false;
          |} else {
          |  ${read}}
          |}"""
    }

    val write: ST = st"""${receivedDataVar} = true;
                        |contents = *m;
                        |monsig_emit();
                        |return ${receivedDataVar};"""
    val mon_write: ST = if(preventBadging) { 
      write 
    } else {
      st"""bool mon_write(const $typeName * m) {
          |  if (mon_get_sender_id() != $MON_WRITE_ACCESS)  {
          |    return false;
          |  } else {
          |    ${write}
          |  }
          |}"""
    }

    val senderSig: ST = if(preventBadging) { st"" } else { st"""
                                                               |int mon_get_sender_id(void);""" }
    val r : ST =
      st"""#include ${typeHeaderFilename}
          |#include "../${Util.DIR_INCLUDES}/${monitorTypeHeaderFilename}.h"
          |
          |${senderSig}int monsig_emit(void);
          |
          |static $typeName contents;
          |bool ${receivedDataVar} = false;
          |
          |bool mon_read($typeName * m) {
          |  ${mon_read}
          |}
          |
          |bool mon_write(const $typeName * m) {
          |  ${mon_write}
          |}"""
    
    return r
  }

  def tbEnqueueDequeue(typeName: String, dim: Z, monitorTypeHeaderFilename: String, typeHeaderFilename: String,
                       preventBadging: B): ST = {

    val mon_dequeue: ST = if(preventBadging) { st"" } else {
      st"""if (mon_get_sender_id() != $MON_READ_ACCESS) {
          |  return false;
          |} else """
    }

    val mon_enqueue: ST = if(preventBadging) { st"" } else {
      st"""if (mon_get_sender_id() != $MON_WRITE_ACCESS) {
          |    return false;
          |} else """
    }

    val r: ST =
      st"""#ifndef $SB_VERIFY
          |#include <stdio.h>
          |#endif // $SB_VERIFY
          |
          |#include ${typeHeaderFilename}
          |#include "../${Util.DIR_INCLUDES}/${monitorTypeHeaderFilename}.h"
          |
          |int mon_get_sender_id(void);
          |int monsig_emit(void);
          |
          |${typeName} contents[${dim}];
          |static uint32_t front = 0;
          |static uint32_t length = 0;
          |
          |static bool is_full(void) {
          |  return length == ${dim};
          |}
          |
          |static bool is_empty(void) {
          |  return length == 0;
          |}
          |
          |bool mon_dequeue(${typeName} * m) {
          |  ${mon_dequeue}if (is_empty()) {
          |    return false;
          |  } else {
          |    *m = contents[front];
          |    front = (front + 1) % ${dim};
          |    length--;
          |    return true;
          |  }
          |}
          |
          |bool mon_enqueue(const ${typeName} * m) {
          |  ${mon_enqueue}if (is_full()) {
          |    return false;
          |  } else {
          |    contents[(front + length) % ${dim}] = *m;
          |    length++;
          |    monsig_emit();
          |    return true;
          |  }
          |}
          |"""
    return r
  }

  def tbRaiseGetEvents(queueSize: Z, monitorTypeHeaderFilename: String, 
                       preventBadging: B): ST = {
  var r: ST = 
    st"""#include <camkes.h>
        |#include <stdio.h>
        |#include <string.h>
        |
        |int32_t num_events = 0;
        |
        |static inline void ignore_result(long long int unused_result) { (void) unused_result; }
        |
        |void mon_send_raise(void) {
        |  int do_emit = 0;
        |  ignore_result(m_lock());
        |  if (num_events < ${queueSize}) {
        |    num_events++;
        |    do_emit = 1;
        |  }
        |  ignore_result(m_unlock());
        |  if (do_emit) {
        |    monsig_emit();
        |  }
        |}
        |
        |int32_t mon_receive_get_events(void) {
        |  ignore_result(m_lock());
        |  int ne = num_events;
        |  num_events = 0;
        |  ignore_result(m_unlock());
        |  return ne;
        |}
        |"""
    
    return r
  }
  
  def tbEnqueueDequeueIhor(typeName: String, dim: Z, monitorTypeHeaderFilename: String, typeHeaderFilename: String,
                           preventBadging: B): ST = {

    val mon_dequeue: ST = if(preventBadging) { st"" } else {
      st"""if (mon_get_sender_id() != $MON_READ_ACCESS) {
          |  return false;
          |} else """
    }

    val mon_enqueue: ST = if(preventBadging) { st"" } else {
      st"""if (mon_get_sender_id() != $MON_WRITE_ACCESS) {
          |    return false;
          |} else """
    }

    val r: ST =
      st"""#ifndef $SB_VERIFY
          |#include <stdio.h>
          |#endif // $SB_VERIFY
          |#include <camkes.h>
          |#include ${typeHeaderFilename}
          |#include "../${Util.DIR_INCLUDES}/${monitorTypeHeaderFilename}.h"
          |
          |struct queue {
          |    int head;
          |    int tail;
          |    int len;
          |    ${typeName} elt[${dim}];
          |} q = {.head=0, .tail=0, .len=0};
          |
          |static bool is_full(void) {
          |  return q.len == ${dim};
          |}
          |
          |static bool is_empty(void) {
          |  return q.len == 0;
          |}
          |
          |bool mon_receive_dequeue(${typeName} * m) {
          |  ${mon_dequeue}if (is_empty()) {
          |    return false;
          |  } else {
          |    m_lock();
          |    *m = q.elt[q.tail];
          |    q.tail = (q.tail + 1) % ${dim};
          |    q.len--;
          |    m_unlock();
          |    return true;
          |  }
          |}
          |
          |bool mon_send_enqueue(const ${typeName} * m) {
          |  ${mon_enqueue}if (is_full()) {
          |    return false;
          |  } else {
          |    m_lock();
          |    q.elt[q.head] = *m;
          |    q.head = (q.head + 1) % ${dim};
          |    q.len++;
          |    m_unlock();
          |    monsig_emit();    
          |    return true;
          |  }
          |}
          |"""
    return r
  }
  
  def seqNumHeader(): ST = {
    return st"""#ifndef _SEQNUM_H_
               |#define _SEQNUM_H_
               |
               |// Typedef for seqNum to make it easy to change the type. Keep these consistent!
               |typedef uintmax_t seqNum_t;
               |#define SEQNUM_MAX UINTMAX_MAX
               |#define PRIseqNum PRIuMAX
               |
               |// DIRTY_SEQ_NUM is used to mark a sampling port message as dirty while it is
               |// being writen. DIRTY_SEQ_NUM is not a valid sequence number. Valid sequence
               |// numbers are from 0 to DIRTY_SEQ_NUM-1 is never a valid sequence number.
               |static const seqNum_t DIRTY_SEQ_NUM = SEQNUM_MAX;
               |
               |#endif"""
  }
  
  def sbSamplingPortGlobalVar(spi: SamplingPortInterface, f: ir.FeatureEnd): ST = {
    val portName = Util.getLastName(f.identifier)
    val globalVarName = s"${Util.brand(portName)}_seqNum"
    return st"$globalVarName"
  }
  
  def sbSamplingPortGlobalVarDecl(spi: SamplingPortInterface, f: ir.FeatureEnd): ST = {
    return st"${StringTemplate.SeqNumType} ${sbSamplingPortGlobalVar(spi, f)};"
  }

  def sbSamplingPortInterface(spi: SamplingPortInterface, f: ir.FeatureEnd): ST = {
    assert(f.category == ir.FeatureCategory.DataPort)

    val portName = Util.getLastName(f.identifier)
    val methodNamePrefix = Util.brand(portName)
    
    val ret: ST = f.direction match {
      case ir.Direction.In => st"bool ${methodNamePrefix}_read(${spi.sel4TypeName} * value);"
      case ir.Direction.Out => st"bool ${methodNamePrefix}_write(const ${spi.sel4TypeName} * value);"
      case _ => halt(s"Unexpected direction ${f.direction}")
    }
    return ret
  }
  
  def sbSamplingPortImplementation(spi: SamplingPortInterface, f: ir.FeatureEnd): ST = {
    assert(f.category == ir.FeatureCategory.DataPort)

    val sharedDataVarName = Util.brand(Util.getLastName(f.identifier))
    val globalVarName = sbSamplingPortGlobalVar(spi, f)

    val ret: ST = f.direction match {
      case ir.Direction.In => st"""bool ${sharedDataVarName}_read(${spi.sel4TypeName} * value) {
                                  |  ${StringTemplate.SeqNumType} new_seqNum;
                                  |  if ( read_${spi.name}(${sharedDataVarName}, value, &new_seqNum) ) {
                                  |    ${globalVarName} = new_seqNum;
                                  |    return true;
                                  |  } else {
                                  |    return false;
                                  |  } 
                                  |}"""
        
      case ir.Direction.Out => st"""bool ${sharedDataVarName}_write(const ${spi.sel4TypeName} * value) {
                                   |  return write_${spi.name}(${sharedDataVarName}, value, &${globalVarName});
                                   |}"""
        
      case _ => halt(s"Unexpected direction ${f.direction}")
    }
    return ret
  }
  
  def sbSamplingPortConfigurationEntry(componentVarName: String, spi: SamplingPortInterface, f: ir.FeatureEnd): ST = {
    val portName = Util.getLastName(f.identifier)
    
    val ret: ST = f.direction match {
      case ir.Direction.In => st"""${componentVarName}.${portName}_access = "R";"""
      case ir.Direction.Out => st"""${componentVarName}.${portName}_access = "W";"""
      case _ => halt(s"Unexpected direction ${f.direction}")
    }
    return ret
  }
  
  def sbAccessRestrictionEntry(componentName: String, varName: String, permission: String): ST = {
    return st"""${componentName}.${varName}_access = "${permission}";"""
  }
  
  val AUX_C_SOURCES: String = "AUX_C_SOURCES"
  val AUX_C_INCLUDES: String = "AUX_C_INCLUDES"

  def cmakeHamrIncludes(hamrIncludeDirs: ISZ[String]): ST = {
    return st"""set(${Util.HAMR_INCLUDES_NAME}
               |  ${(hamrIncludeDirs, "\n")}
               |)"""
  }

  def cmakeAuxSources(auxCSources: ISZ[String], auxHDirectories: ISZ[String]): ST = {
    return st"""set(${AUX_C_SOURCES} ${(auxCSources, " ")})
               |set(${AUX_C_INCLUDES} ${(auxHDirectories, " ")})"""
  }

  def cmakeHamrLib(hamrStaticLib: String): ST = {
    return st"set(${Util.HAMR_LIB_NAME} ${hamrStaticLib})"
  }

  def cmakeHamrExecuteProcess(): ST = {
    return st"""execute_process(COMMAND bash -c "$${CMAKE_CURRENT_LIST_DIR}/compile-hamr-lib.sh")"""
  }

  def cmakeLists(cmakeVersion: String, rootServer: String, entries: ISZ[ST]): ST = {
    return st"""cmake_minimum_required(VERSION ${cmakeVersion})
               |
               |project (${rootServer} C)
               |
               |add_definitions(-DCAMKES)
               |
               |${(entries, "\n\n")}
               |
               |DeclareCAmkESRootserver(${rootServer}.camkes)
               |"""
  }

  def cmakeComponent(componentName: String, sources: ISZ[String], includes: ISZ[String], hasAux: B,
                     hasHamrIncl: B, hasHamrLib: B): ST = {
    var srcs: ISZ[ST] = ISZ()
    if(hasAux) { srcs = srcs :+ st"$${${AUX_C_SOURCES}} " }
    if(sources.nonEmpty) { srcs = srcs :+ st"""${(sources, " ")}""" }

    var incls: ISZ[ST] = ISZ()
    if(hasAux) { incls = incls :+ st"$${${AUX_C_INCLUDES}} " }
    if(hasHamrIncl){ incls = incls :+ st"$${${Util.HAMR_INCLUDES_NAME}} "}
    if(includes.nonEmpty) { incls = incls :+ st"""${(includes, " ")}""" }

    val libs: ST = if(hasHamrLib) { st"LIBS $${${Util.HAMR_LIB_NAME}}"}
    else { st"" }

    val r: ST =
      st"""DeclareCAmkESComponent(${componentName}
          |  SOURCES $srcs
          |  INCLUDES $incls
          |  ${libs}
          |)"""
    return r
  }

  def configurationPriority(name: String, priority: Z): ST = {
    return st"${name}.priority = ${priority};"
  }

  def configurationControlStackSize(name: String, size: Z): ST = {
    return st"${name}._control_stack_size = ${size};"
  }

  def configurationStackSize(name: String, size: Z): ST = {
    return st"${name}._stack_size = ${size};"
  }

  val SEM_WAIT: String = Util.brand("dispatch_sem_wait")
  val SEM_POST: String = Util.brand("dispatch_sem_post")

  def componentTypeImpl(filename: String, 
                        includes: ISZ[ST], 
                        blocks: ISZ[ST],
                        preInits: ISZ[ST],
                        postInits: ISZ[ST],
                        runPreEntries: ISZ[ST], 
                        runLoopEntries: ISZ[ST],
                        isSporadic: B): ST = {
    val initialLock: ST = if(isSporadic) { st"" } else { st"""// Initial lock to await dispatch input.
                                                             |MUTEXOP(${SEM_WAIT}())"""}
    
    val preInit: ST = if(preInits.nonEmpty) {
      st"""
          |void pre_init(void) {
          |  ${(preInits, "\n")}
          |}"""
    } else { st"" }
    
    val postInit: ST = if(postInits.nonEmpty) {
      st"""
          |void post_init(void){
          |  ${(postInits, "\n")}
          |}"""
    } else { st"" }
    
    val ret:ST = st"""#include "../${Util.DIR_INCLUDES}/${filename}.h"
                     |${(includes, "\n")}
                     |#include <string.h>
                     |#include <camkes.h>
                     |
                     |${(blocks, "\n\n")}
                     |${preInit}
                     |${postInit}
                     |
                     |/************************************************************************
                     | * int run(void)
                     | * Main active thread function.
                     | ************************************************************************/
                     |int run(void) {
                     |  ${(runPreEntries, "\n")}
                     |  ${initialLock}
                     |  for(;;) {
                     |    MUTEXOP(${SEM_WAIT}())
                     |    
                     |    ${(runLoopEntries, "\n")}
                     |  }
                     |  return 0;
                     |}
                     |"""
    return ret
  }

  def componentInitializeEntryPoint(componentName: String, methodName: String): (ST, ST) = {
    val init: String = Util.brand(s"entrypoint_${componentName}_initializer")
    val ret: ST =
      st"""/************************************************************************
          | *  ${init}:
          | *
          | * This is the function invoked by an active thread dispatcher to
          | * call to a user-defined entrypoint function.  It sets up the dispatch
          | * context for the user-defined entrypoint, then calls it.
          | *
          | ************************************************************************/
          |void ${init}(const int64_t * in_arg) {
          |  ${methodName}((int64_t *) in_arg);
          |}"""
    val dummy = Util.brand("dummy")
    val runEntry: ST = st"""{
                           |  int64_t ${dummy};
                           |  ${init}(&${dummy});
                           |}"""
    return (ret, runEntry)
  }

  def cEventNotificiationHandler(handlerName: String, regCallback: String): ST = {
    val ret: ST =
      st"""static void ${handlerName}(void * unused) {
          |  MUTEXOP(${SEM_POST}())
          |  CALLBACKOP(${regCallback}(${handlerName}, NULL));
          |}"""
    return ret
  }

  def cRegCallback(handlerName: String, regCallback: String): ST = {
    val ret: ST = st"CALLBACKOP(${regCallback}(${handlerName}, NULL));"
    return ret
  }

  val VAR_PERIODIC_OCCURRED : String = Util.brand("occurred_periodic_dispatcher")
  val VAR_PERIODIC_TIME : String = Util.brand("time_periodic_dispatcher")
  val METHOD_PERIODIC_CALLBACK : String = s"${TimerUtil.componentNotificationName(None())}_callback"
  
  def periodicDispatchElems(componentId: String, timerHookedUp: B) : ST = {
    var h: String = s"${Util.brand("timer_time()")} / 1000LL"
    if(!timerHookedUp) {
      h = s"0; // ${h} -- timer connection disabled"
    }
    
    val ret = st"""static bool ${VAR_PERIODIC_OCCURRED};
                  |static int64_t ${VAR_PERIODIC_TIME};
                  |
                  |/************************************************************************
                  | * periodic_dispatcher_write_int64_t
                  | * Invoked from remote periodic dispatch thread.
                  | *
                  | * This function records the current time and triggers the active thread
                  | * dispatch from a periodic event.  Note that the periodic dispatch
                  | * thread is the *only* thread that triggers a dispatch, so we do not
                  | * mutex lock the function.
                  | *
                  | ************************************************************************/
                  |
                  |bool periodic_dispatcher_write_int64_t(const int64_t * arg) {
                  |    ${VAR_PERIODIC_OCCURRED} = true;
                  |    ${VAR_PERIODIC_TIME} = *arg;
                  |    MUTEXOP(${SEM_POST}());
                  |    return true;
                  |}
                  |
                  |void ${METHOD_PERIODIC_CALLBACK}(void *_ UNUSED) {
                  |   // we want time in microseconds, not nanoseconds, so we divide by 1000.
                  |   int64_t ${VAR_PERIODIC_TIME} = ${h};
                  |   (void)periodic_dispatcher_write_int64_t(&${VAR_PERIODIC_TIME});
                  |   ${registerPeriodicCallback()}
                  |}
                  |"""
    return ret
  }

  def registerPeriodicCallback(): ST = {
    val notificationName = TimerUtil.componentNotificationName(None())
    return st"CALLBACKOP(${notificationName}_reg_callback(${METHOD_PERIODIC_CALLBACK}, NULL));"
  }

  def drainPeriodicQueue(componentName: String, userEntrypoint: String): (ST, ST) = {
    val methodName = Util.brand(s"entrypoint_${componentName}_periodic_dispatcher")

    val impl = st"""void ${methodName}(const int64_t * in_arg) {
                   |  ${userEntrypoint}((int64_t *) in_arg);
                   |}"""

    val drain = st"""if(${VAR_PERIODIC_OCCURRED}){
                    |  ${VAR_PERIODIC_OCCURRED} = false;
                    |  ${methodName}(&${VAR_PERIODIC_TIME});
                    |}"""
    return (impl, drain)
  }

  def hamrGetInstanceName(basePackageName: String, c: Component): ST = {
    return st"${basePackageName}_${Util.getName(c.identifier)}"
  }
  
  def hamrIntialise(basePackageName: String, c: Component): ST = {
    val instanceName = hamrGetInstanceName(basePackageName, c)
    return st"""// initialise slang-embedded components/ports
               |${instanceName}_App_initialise(SF seed);
               |"""
  }

  def hamrInitialiseEntrypoint(basePackageName: String, c: Component): ST = {
    val instanceName = hamrGetInstanceName(basePackageName, c)
    return st"""// call the component's initialise entrypoint
               |art_Bridge_EntryPoints_initialise_(SF ${instanceName}_App_entryPoints(SF));
               |"""
  }

  def hamrGetArchId(basePackageName: String, c: ir.Component): String = {
    val n =  org.sireum.ops.ISZOps(c.identifier.name).foldLeft((r: String, s : String) => s"${r}_${s}", "")
    return s"${basePackageName}_Arch${n}"
  }

  def hamrRunLoopEntries(basePackageName: String, c: Component): ISZ[ST] = {
    val instanceName = hamrGetInstanceName(basePackageName, c)
    return ISZ(st"transferIncomingDataToArt();", st"", 
      st"${instanceName}_App_compute(SF);")
  }

  def hamrSlangPayloadType(c : ir.Classifier, base: String) : String = {
    val r = StringUtil.replaceAll(StringUtil.replaceAll(c.name, "::", "_"), ".", "_")
    return s"${base}_${r}_Payload"
  }

  def hamrSendViaCAmkES(srcPort: ir.FeatureEnd, dstComponent: ir.Component, dstFeature: ir.FeatureEnd, connectionIndex: Z,
                        basePackageName: String, typeMap: HashSMap[String, ir.Component]) : ST = {
    val dstComponentName = Util.getLastName(dstComponent.identifier)
    val dstFeatureName = Util.getLastName(dstFeature.identifier)
    
    def handleDataPort(): ST = {
      val suffix: String = if(srcPort.category == ir.FeatureCategory.DataPort) "write" else "enqueue"
      val srcEnqueue = Util.brand(s"${Util.getLastName(srcPort.identifier)}${connectionIndex}_${suffix}")
      val camkesType = Util.getClassifierFullyQualified(srcPort.classifier.get)
      val ct = Util.getSel4TypeName(typeMap.get(camkesType).get)

      val slangPayloadType = hamrSlangPayloadType(srcPort.classifier.get, basePackageName)
      return st"""${slangPayloadType} payload = (${slangPayloadType}) d;
                 |
                 |// convert Slang type to CAmkES type
                 |${ct} val;
                 |convertTo_${ct}(payload, &val);
                 |
                 |// deliver payload to ${dstComponentName}'s ${dstFeatureName} port via CAmkES
                 |${srcEnqueue}(&val);"""
    }
    
    def handleEventPort(): ST = {
      val srcWrite = Util.getEventPortSendReceiveMethodName(srcPort)
      return st"""// event port - can ignore the Slang Empty payload
                 |art_Empty payload = (art_Empty) d;
                 |
                 |// send event to ${dstComponentName}'s ${dstFeatureName} port via CAmkES
                 |${srcWrite}();"""
    }
    
    val ret: ST = srcPort.category match {
      case ir.FeatureCategory.DataPort => handleDataPort()
      case ir.FeatureCategory.EventDataPort => handleDataPort()
      case ir.FeatureCategory.EventPort => handleEventPort()
      case x => halt(s"Not handling ${x}")
    }
    
    return ret
  }

  def hamrDrainQueue(f: ir.FeatureEnd, basePackageName: String, typeMap: HashSMap[String, ir.Component]): ST = {
    val portName = Util.getLastName(f.identifier)
    val camkesId = s"${portName}_id"

    def handleEventPort(): ST = {
      val srcRead = Util.getEventPortSendReceiveMethodName(f)
      return st"""{
                 |  while(${srcRead}()){
                 |    // event port - ART requires an Empty payload be sent
                 |    DeclNewart_Empty(payload);
                 | 
                 |    // deliver payload via ART
                 |    camkes_In_Port_Data_Transfer(${camkesId}, (art_DataContent) &payload);
                 |  }
                 |}"""
    }
    def handleDataPort(): ST = {
      val suffix: String = if(f.category == ir.FeatureCategory.DataPort) "read" else "dequeue"
      val dequeue = Util.brand(s"${portName}_${suffix}")
      val camkesType = Util.getClassifierFullyQualified(f.classifier.get)
      val ct = Util.getSel4TypeName(typeMap.get(camkesType).get)
      val condType: String = if(Util.isEventPort(f)) "while" else "if"
      val slangPayloadType = hamrSlangPayloadType(f.classifier.get, basePackageName)

      return st"""{
                 |  ${ct} val;
                 |  ${condType}(${dequeue}((${ct} *) &val)){
                 |    // convert to slang payload
                 |    DeclNew${slangPayloadType}(payload);
                 |    convertTo_${slangPayloadType}(val, &payload);
                 |
                 |    // deliver payload via ART
                 |    camkes_In_Port_Data_Transfer(${camkesId}, (art_DataContent) &payload);
                 |  }
                 |}
                 |"""  
    }
    
    val ret: ST = f.category match {
      case ir.FeatureCategory.EventPort => handleEventPort()
      case ir.FeatureCategory.DataPort => handleDataPort()
      case ir.FeatureCategory.EventDataPort => handleDataPort()
      case x => halt(s"Not handling ${x}")
    }
    return ret
  }

  def hamrIPC(numPorts: Z, basePackageName: String): (ST, ST) = {
    val impl = st"""#include <all.h>
                   |#include <ipc.h>
                   |
                   |static union Option_8E9F45 camkes_buffer[${numPorts}] = { 0 };
                   |
                   |Z ${basePackageName}_SharedMemory_create(STACK_FRAME Z id) {
                   |  //printf("${basePackageName}_Shared_Memory_create called with id %i\n", id);
                   |  
                   |  DeclNewNone_964667(t_0);
                   |  None_964667_apply(CALLER &t_0);
                   |  Type_assign((camkes_buffer + id), (&t_0), sizeof(union Option_8E9F45));
                   |
                   |  return -1;
                   |}
                   |
                   |void ${basePackageName}_SharedMemory_receive(STACK_FRAME art_DataContent result, Z port) {
                   |  printf("${basePackageName}_SharedMemory_receive called with port %i -- NOT IMPLEMENTED\n", port);
                   |}
                   |
                   |void ${basePackageName}_SharedMemory_receiveAsync(STACK_FRAME Option_8E9F45 result, Z port) {
                   |  union Option_8E9F45 p = camkes_buffer[port];
                   |
                   |  if (p.type == TSome_D29615) {
                   |      Type_assign(result, &p, sizeOf((Type) &p));
                   |      memset(camkes_buffer + port, 0, sizeof(union Option_8E9F45));
                   |  } else {
                   |      result->type = TNone_964667;
                   |  }
                   |}
                   |
                   |Unit ${basePackageName}_SharedMemory_send(STACK_FRAME Z destAppid, Z destPortId, art_DataContent d) {
                   |  printf("${basePackageName}_SharedMemory_send called with port %i -- NOT IMPLEMENTED\n", destPortId);
                   |}
                   |
                   |B ${basePackageName}_SharedMemory_sendAsync(STACK_FRAME Z destAppId, Z destPortId, art_DataContent d) {
                   |  // printf("${basePackageName}_SharedMemory_sendAsync called with destPortId %i\n", destPortId);
                   |  
                   |  camkes_sendAsync(destPortId, d);
                   |
                   |  return T;
                   |}
                   |
                   |Unit ${basePackageName}_SharedMemory_remove(STACK_FRAME Z id) {
                   |  printf("${basePackageName}_SharedMemory_remove called with %i -- NOT IMPLEMENTED\n", id);
                   |}
                   |
                   |Unit ${basePackageName}_Process_sleep(STACK_FRAME Z n) {}
                   |
                   |void camkes_In_Port_Data_Transfer (Z port, art_DataContent d) {
                   |  union Option_8E9F45 p = camkes_buffer[port];
                   |  camkes_buffer[port].type = TSome_D29615;
                   |  Type_assign(&(camkes_buffer[port].Some_D29615.value), d, sizeOf((Type) d));
                   |}
                   |"""

    val header = st"""#ifndef IPC_H
                     |#define IPC_H
                     |#include <all.h>
                     |
                     |static const int seed = 1;
                     |
                     |// transfer data from CAmkES to ART
                     |void camkes_In_Port_Data_Transfer (Z destPortId, art_DataContent payload);
                     |
                     |// transfer data from ART to CAmkES
                     |void camkes_sendAsync(Z destPortId, art_DataContent payload);
                     |
                     |#endif"""
    return (header, impl)
  }

  def samplingPortHeader(s: SamplingPortInterface): ST = {
    val spName = s.name
    val macroName = StringUtil.toUpperCase(s"${spName}_h")
    val portType = s.sel4TypeName
    
    val ret = st"""#ifndef ${macroName}
#define ${macroName}

#include "seqNum.h"

// Sampling port message with bool data
typedef struct ${spName} {

  // The sampling port message data.
  ///
  // TODO: How do we handle differnet data types?  Possible options:
  //
  //   - HAMR could generate a dedicated struct for each data port type. In
  //     the long run this may be the best options since AADL can specify the
  //     message type.
  //
  //   - Generalize this struct with some C wizardry. Would it help to split
  //     this into two data parts, one for the data and one for the sequence
  //     number?
  //
  ${portType} data;
  
  // Sequence number incremented by the writer every time the sampling port is
  // written. Read by the reciever to detect dropped messages and incoherant
  // message reads.  An incoherant message is one that is formed of parts of
  // more than one message.  An incoherent message can occure when writing
  // happens durring read. If the component runs long enough, this counter
  // will wrap back to zero.  This causes no problems unless the reciever is
  // delayed for the wrap time. In that case the reciever may not detect
  // dropped or incoherent messags. But if the reciver is delayed for that
  // long the system is probably in a very bad state. Also see DIRTY_SEQ_NUM
  // above.
  //
  // TODO: Currently using ggc builtin _Atomic. Would like to use c11 std, but
  // have not figured out how to do this int the seL4 cmake build environment.
  _Atomic seqNum_t seqNum;  

} ${s.structName};

void init_${spName}(${s.structName} *port, seqNum_t *seqNum);

bool write_${spName}(${s.structName} *port, const ${portType} *data, seqNum_t *seqNum);

bool read_${spName}(${s.structName} *port, ${portType} *data, seqNum_t *seqNum);

#endif
"""
    return ret
  }
  
  def samplingPortImpl(s: SamplingPortInterface): ST = {
    val spName = s.name
    val spType = s"${spName}_t"
    val portType = s.sel4TypeName
    
    val ret = st"""
#include <camkes.h>
#include <stdio.h>
#include <sel4/sel4.h>
#include <utils/util.h>
#include <sel4utils/util.h>
#include <sel4utils/helpers.h>

#include "${spName}.h"

void init_${spName}(${spType} *port, seqNum_t *seqNum) {
  *seqNum = 0; // First message sequence number will be 1.
  port->seqNum = DIRTY_SEQ_NUM;
}

// Write message to a sampling port (data type: int)
//
// Returns true when sucessful. Otherwise returns false. Currently there is no
// way to fail and true is alwasy returned. But this may change in the
// future. seqNum is incremented when a message is succefully sent. seqNum
// should not be modified otherwise.
//
// TODO: Encapsulate this better. seqNum state should be maintained internaly. Possible solutions:
//
//    - Allow write to have read access to dataport. Then seqNum is simply in the data port.
//
//    - Create a wrapper struct.
//
// TODO: Currently using ggc builtin __atomic_thread_fence(__ATOMIC_RELEASE).
// Would like to use c11 std, but have not figured out how to do this int the
// seL4 cmake build environment.
bool write_${spName}(${spType} *port, const ${portType} *data, seqNum_t *seqNum) {
  // Mark the message dirty BEFORE we start writting.
  port->seqNum = DIRTY_SEQ_NUM;
  // Release memory fence - ensure write above to seqNum happens BEFORE reading data
  __atomic_thread_fence(__ATOMIC_RELEASE);
  // Write the data
  port->data = *data;
  // Increment the sequence number. We are the only writer of seqNum, so
  // increment does not have to be atomic.
  *seqNum = (*seqNum + 1) % DIRTY_SEQ_NUM;
  port->seqNum = *seqNum;
  // Release memory fence - ensure write above to seqNum happens BEFORE continuing
  __atomic_thread_fence(__ATOMIC_RELEASE);
  // Can't fail for now.
  return true;
}

// Read a message from a sampling port (data type: int)
//
// Return true upon successful read. Data is updated with the read
// message. The sequence number of the message is also returned. The messaage,
// might be tha same previously read. The sequences number can be used to
// detect rereading the same message or dropped messages.
//
// Return false if we fail to read a message. For now the only way to fail is
// when we detect the possibliliy of a write durring read. In this case data
// may be incoherent and should not be used. Sequence number is set to
// DIRTY_SEQ_NUM;
//
// TODO: Currently using ggc builtin __atomic_thread_fence(__ATOMIC_ACQUIRE).
// Would like to use c11 std, but have not figured out how to do this int the
// seL4 cmake build environment.
bool read_${spName}(${spType} *port, ${portType} *data, seqNum_t *seqNum) {
  seqNum_t newSeqNum = port->seqNum;
  // Acquire memory fence - Read seqNum BEFORE reading data
  __atomic_thread_fence(__ATOMIC_ACQUIRE);
  *data = port->data;
  // Acquire memory fence - Read data BEFORE reading seqNum again 
  //atomic_thread_fence(memory_order_acquire);
  __atomic_thread_fence(__ATOMIC_ACQUIRE);
  // The following logic will NOT catch case where the writer wrapped
  // sequence numbers since our last read. For this to happen, this reader
  // would have to be delayed for the entire time to wrap. 
  if (newSeqNum != DIRTY_SEQ_NUM && newSeqNum == port->seqNum) {
    // Message data is good.  Write did not occure durring read. 
    *seqNum = newSeqNum;
    return true;
  } else {
    // Writer may have updated data while we were reading. Do not use possibly incoherent data.
    *seqNum = DIRTY_SEQ_NUM;
    return false;
  }
}
"""
    return ret
  }
  
  def ifEsleHelper(options: ISZ[(ST, ST)], optElse: Option[ST]): ST = {
    val first: Option[(ST, ST)] = if(options.size > 0) { Some(options(0)) } else { None() }
    val rest: ISZ[(ST, ST)] = if(options.size > 1) { org.sireum.ops.ISZOps(options).drop(1) } else { ISZ() }
    return ifElseST(first, rest, optElse)
  }

  def ifElseST(ifbranch: Option[(ST, ST)], elsifs: ISZ[(ST, ST)], els: Option[ST]): ST = {

    var body = st""

    if(ifbranch.nonEmpty) {
      body = st"""if(${ifbranch.get._1}) {
                 |  ${ifbranch.get._2}
                 |} """
    }

    if(elsifs.nonEmpty) {
      val ei = elsifs.map((x: (ST, ST)) => st"""else if(${x._1}) {
                                               |  ${x._2}
                                               |} """)
      body = st"""${body}${ei}"""
    }

    if(els.nonEmpty) {
      if(ifbranch.nonEmpty) {
        body = st"""${body}else {
                   |  ${els.get}
                   |}"""
      } else {
        body = els.get
      }
    }

    return body
  }
}
