package client.nilore.modules.impl.render;

import client.nilore.modules.Category;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 模块信息注解
 * 用于标记模块的名称、描述和分类
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleInfo {

    /**
     * 模块名称（显示在 ClickGUI 中）
     */
    String name() default "";

    /**
     * 模块描述（鼠标悬停时显示）
     */
    String description() default "";

    /**
     * 模块分类
     */
    Category category() default Category.RENDER;

    /**
     * 模块是否默认启用
     */
    boolean enabled() default false;
}