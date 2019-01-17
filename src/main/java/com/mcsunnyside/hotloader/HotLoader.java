package com.mcsunnyside.hotloader;

import static moe.yushi.authlibinjector.AuthlibInjector.PROP_API_ROOT;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;

import org.bukkit.plugin.java.JavaPlugin;

import com.mojang.authlib.HttpAuthenticationService;

import moe.yushi.authlibinjector.AuthlibInjector;

public class HotLoader extends JavaPlugin{
	public static HotLoader instance;
	@Override
	public void onEnable() {
		instance=this;
		getLogger().info("Loading Authlib-Injector Hotloader...");
		saveDefaultConfig();
		System.setProperty(PROP_API_ROOT, HotLoader.instance.getConfig().getString("api-root"));
		classLoader(this.getFile(), "moe.yushi.authlibinjector.javaagent.AuthlibInjectorPremain", "start",
				new Object[0], new Class[0]);
		getLogger().info("Successfully loaded AuthlibInjector Hotloader.");
		try {
			Field a = com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService.class.getDeclaredField("BASE_URL");
			changeString(a);
			a = com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService.class.getDeclaredField("CHECK_URL");
			changeURL(a);
			a = com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService.class.getDeclaredField("JOIN_URL");
			changeURL(a);
			a = com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService.class
					.getDeclaredField("WHITELISTED_DOMAINS");
			removeFinal(a);
			change(a, null, new String[] { ".minecraft.net", ".mojang.com", HotLoader.instance.getConfig().getString("api-root-domain")});
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	public void changeString(Field field)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		removeFinal(field);
		field.setAccessible(true);
		change(field, null, AuthlibInjector.unit.transformURL(field.get(null).toString()).get());
	}

	public void changeURL(Field field)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		removeFinal(field);
		field.setAccessible(true);
		change(field, null, HttpAuthenticationService
				.constantURL(AuthlibInjector.unit.transformURL(field.get(null).toString()).get()));
	}

	public void removeFinal(Field field)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		field.setAccessible(true);
		Field modifiersField = Field.class.getDeclaredField("modifiers");
		modifiersField.setAccessible(true);
		modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
	}

	public void change(Field field, Object obj, Object value)
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		field.setAccessible(true);
		field.set(obj, value);
	}

	public static void classLoader(File file, String className, String methodName, Object[] args,
			Class<?>[] parameterTypes) {
		try {
			URL url = file.toURI().toURL();
			URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
			Method add = URLClassLoader.class.getDeclaredMethod("addURL", new Class[] { URL.class });
			add.setAccessible(true);
			add.invoke(urlClassLoader, new Object[] { url });
			urlClassLoader.loadClass(className);
			Class<?> c = urlClassLoader.loadClass(className);
			if (args == null) {
				c.getMethod(methodName, parameterTypes).invoke(c.newInstance());
			} else {
				c.getMethod(methodName, parameterTypes).invoke(c.newInstance(), args);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
