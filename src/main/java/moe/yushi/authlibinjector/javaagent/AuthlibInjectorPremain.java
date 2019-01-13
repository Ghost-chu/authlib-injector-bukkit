package moe.yushi.authlibinjector.javaagent;

import static moe.yushi.authlibinjector.AuthlibInjector.PROP_API_ROOT;
import static moe.yushi.authlibinjector.AuthlibInjector.bootstrap;
import static moe.yushi.authlibinjector.AuthlibInjector.nonTransformablePackages;
import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.logging.Level;

import cn.apisium.lateragent.AgentApi;
import moe.yushi.authlibinjector.InjectorInitializationException;
import moe.yushi.authlibinjector.util.Logging;

public class AuthlibInjectorPremain {

	static {
		Logging.init();
	}

	public static void premain(String arg, Instrumentation instrumentation) {
		try {
			initInjector(arg, instrumentation, false);
		} catch (InjectorInitializationException e) {
			Logging.LAUNCH.log(Level.FINE, "A known exception has occurred", e);
			System.exit(1);
		} catch (Throwable e) {
			Logging.LAUNCH.log(Level.SEVERE, "An exception has occurred, exiting", e);
			System.exit(1);
		}
	}

	public static void agentmain(String arg, Instrumentation instrumentation) {
		try {
			Logging.LAUNCH.info("Launched from agentmain");
			initInjector(arg, instrumentation, true);
		} catch (InjectorInitializationException e) {
			Logging.LAUNCH.log(Level.FINE, "A known exception has occurred", e);
		} catch (Throwable e) {
			Logging.LAUNCH.log(Level.SEVERE, "An exception has occurred", e);
		}
	}

	public static void initInjector(String arg, Instrumentation instrumentation, boolean needsRetransform) {
		setupConfig(arg);

		boolean retransformSupported = instrumentation.isRetransformClassesSupported();
		// boolean retransformEnabled = retransformSupported && needsRetransform;
		bootstrap(x -> instrumentation.addTransformer(x, true));

		if (needsRetransform) {
			if (retransformSupported) {
				Logging.TRANSFORM.info("Start retransforming");
				doRetransform(instrumentation);
			} else {
				Logging.TRANSFORM.warning("Retransform is not supported");
			}
		}
	}

	public static void start() {
		moe.yushi.authlibinjector.javaagent.AuthlibInjectorPremain.premain(PROP_API_ROOT,
				AgentApi.getInstrumentation());
		moe.yushi.authlibinjector.javaagent.AuthlibInjectorPremain.doRetransform(AgentApi.getInstrumentation());
	}

	private static void setupConfig(String arg) {
		if (arg != null && !arg.isEmpty()) {
			System.setProperty(PROP_API_ROOT, arg);
		}
	}

	public static void doRetransform(Instrumentation instrumentation) {
		try {
			long t0 = System.currentTimeMillis();
			Class<?>[] classToRetransform = getClassesToRetransform(instrumentation);
			if (classToRetransform.length > 0) {
				instrumentation.retransformClasses(classToRetransform);
			}
			long t1 = System.currentTimeMillis();
			Logging.TRANSFORM.info("Retransform finished in " + (t1 - t0) + "ms");
		} catch (Throwable e) {
			Logging.TRANSFORM.log(Level.SEVERE, "Failed to retransform", e);
		}
	}

	private static Class<?>[] getClassesToRetransform(Instrumentation instrumentation) {
		Class<?>[] loadedClasses = instrumentation.getAllLoadedClasses();
		Class<?>[] dest = new Class[loadedClasses.length];
		int idx = 0;
		for (Class<?> clazz : loadedClasses) {
			if (instrumentation.isModifiableClass(clazz)) {
				boolean toRetransform = true;
				for (String nonTransformablePackage : nonTransformablePackages) {
					if (clazz.getName().startsWith(nonTransformablePackage)) {
						toRetransform = false;
						break;
					}
				}
				if (clazz.getClassLoader() == null || clazz.isArray() || clazz.isPrimitive() || clazz.isSynthetic()) {
					toRetransform = false;
				}

				if (toRetransform) {
					dest[idx++] = clazz;
				}
			}
		}
		Logging.TRANSFORM.fine("Loaded " + loadedClasses.length + " classes, " + idx + " to retransform");
		return Arrays.copyOf(dest, idx);
	}

}
