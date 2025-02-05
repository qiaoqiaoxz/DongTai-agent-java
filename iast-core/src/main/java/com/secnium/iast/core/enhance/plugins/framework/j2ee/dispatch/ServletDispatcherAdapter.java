package com.secnium.iast.core.enhance.plugins.framework.j2ee.dispatch;

import com.secnium.iast.core.enhance.IastContext;
import com.secnium.iast.core.enhance.plugins.AbstractClassVisitor;
import com.secnium.iast.core.util.AsmUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import com.secnium.iast.core.util.LogUtils;

/**
 * @author dongzhiyong@huoxian.cn
 */
public class ServletDispatcherAdapter extends AbstractClassVisitor {

    private final Logger logger = LogUtils.getLogger(getClass());
    private final static String HTTP_SERVLET_REQUEST = " javax.servlet.http.HttpServletRequest".substring(1);
    private final static String HTTP_SERVLET_RESPONSE = " javax.servlet.http.HttpServletResponse".substring(1);
    private final static String SERVLET_REQUEST = " javax.servlet.ServletRequest".substring(1);
    private final static String SERVLET_RESPONSE = " javax.servlet.ServletResponse".substring(1);
    private final static String FILTER_CHAIN = " javax.servlet.FilterChain".substring(1);
    private final static String FACES_SERVLET = " javax.faces.webapp.FacesServlet".substring(1);


    private final boolean isFaces;

    ServletDispatcherAdapter(ClassVisitor classVisitor, IastContext context) {
        super(classVisitor, context);
        this.isFaces = FACES_SERVLET.equals(context.getClassName());
    }

    @Override
    public boolean hasTransformed() {
        return transformed;
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        Type[] typeOfArgs = Type.getArgumentTypes(desc);
        String signCode = AsmUtils.buildSignature(context.getClassName(), name, desc);
        if (isService(name, typeOfArgs) || (this.isFaces && isFacesArgs(typeOfArgs))) {
            if (logger.isDebugEnabled()) {
                logger.debug("Adding HTTP tracking for type {}", context.getClassName());
            }

            mv = new ServletDispatcherAdviceAdapter(mv, access, name, desc, signCode, context);
            transformed = true;
        }
        if (transformed) {
            if (logger.isDebugEnabled()) {
                logger.debug("rewrite method {}.{} for listener[id={}]", context.getClassName(), name, context.getListenId());
            }
        }

        return mv;
    }

    /**
     * 检查是否为http入口方法（service/doFilter)
     *
     * @param name       方法名称
     * @param typeOfArgs 方法参数
     * @return true-是http入口方法，falst-非http入口方法
     */
    private boolean isService(String name, Type[] typeOfArgs) {
        if ("service".equals(name)) {
            return isServiceArgs(typeOfArgs);
        } else {
            return "doFilter".equals(name) && (isFilterArg(typeOfArgs) || isFilterChainArg(typeOfArgs));
        }

    }

    private boolean isServiceArgs(Type[] typeOfArgs) {
        return typeOfArgs.length == 2 &&
                HTTP_SERVLET_REQUEST.equals(typeOfArgs[0].getClassName()) &&
                HTTP_SERVLET_RESPONSE.equals(typeOfArgs[1].getClassName());
    }

    private boolean isFacesArgs(Type[] typeOfArgs) {
        if (typeOfArgs.length != 2) {
            return false;
        }
        String arg1Classname = typeOfArgs[0].getClassName();
        String arg2Classname = typeOfArgs[1].getClassName();
        return SERVLET_REQUEST.equals(arg1Classname) && SERVLET_RESPONSE.equals(arg2Classname);
    }

    private boolean isFilterArg(Type[] typeOfArgs) {
        return typeOfArgs.length == 3 &&
                SERVLET_REQUEST.equals(typeOfArgs[0].getClassName()) &&
                SERVLET_RESPONSE.equals(typeOfArgs[1].getClassName()) &&
                FILTER_CHAIN.equals(typeOfArgs[2].getClassName());
    }

    private boolean isFilterChainArg(Type[] typeOfArgs) {
        return typeOfArgs.length == 2 &&
                SERVLET_REQUEST.equals(typeOfArgs[0].getClassName()) &&
                SERVLET_RESPONSE.equals(typeOfArgs[1].getClassName());
    }
}
