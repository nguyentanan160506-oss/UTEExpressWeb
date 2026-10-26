package com.ute.uteexpress.operations;

import com.ute.uteexpress.repository.ShipmentRepository;
import java.util.concurrent.*;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.*;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;

/** No authorization/repository mocks. Probe pauses a real repository invocation at a defined boundary. */
@TestConfiguration(proxyBeanMethods=false)
@Import(LogisticsJourneyConfig.class)
class SystemSecurityIntegrationConfig {
    @Bean static WorkflowProbe workflowProbe() {return new WorkflowProbe();}
    static final class WorkflowProbe implements BeanPostProcessor {
        private final ThreadLocal<Runnable> beforeLock=new ThreadLocal<>();
        void arm(CountDownLatch reached,CountDownLatch release) {
            beforeLock.set(()->{reached.countDown();try {if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Workflow probe timed out");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}});
        }
        void clear() {beforeLock.remove();}
        @Override public Object postProcessAfterInitialization(Object bean,String name) {
            if(bean instanceof ShipmentRepository && bean instanceof Advised proxy) {
                var pointcut=new NameMatchMethodPointcut();pointcut.setMappedName("findForLifecycle");
                proxy.addAdvisor(0,new DefaultPointcutAdvisor(pointcut,(MethodInterceptor)call->{var hook=beforeLock.get();if(hook!=null){beforeLock.remove();hook.run();}return call.proceed();}));
            }return bean;
        }
    }
}
