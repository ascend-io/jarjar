/**
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pantsbuild.jarjar;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import java.util.*;
import java.util.regex.Pattern;

class PackageRemapper extends Remapper
{
    private static final String RESOURCE_SUFFIX = "RESOURCE";
    
    private static final Pattern ARRAY_FOR_NAME_PATTERN
        = Pattern.compile("\\[L[\\p{javaJavaIdentifierPart}\\.]+?;");

    private final List<Wildcard> wildcards;
    private final Map<String, String> typeCache = new HashMap<String, String>();
    private final Map<String, String> pathCache = new HashMap<String, String>();
    private final Map<Object, String> valueCache = new HashMap<Object, String>();
    private final boolean verbose;

    public PackageRemapper(List<Rule> ruleList, boolean verbose) {
        this.verbose = verbose;
        wildcards = PatternElement.createWildcards(ruleList);
    }

    // also used by KeepProcessor
    static boolean isArrayForName(String value) {
      return ARRAY_FOR_NAME_PATTERN.matcher(value).matches();
    }

    public String map(String key) {
        String s = typeCache.get(key);
        if (s == null) {
            s = replaceHelper(key);
            if (key.equals(s))
                s = null;
            typeCache.put(key, s);
        }
        return s;
    }

    public String mapPath(String path) {
        String s = pathCache.get(path);
        if (s == null) {
            s = path;
            int slash = s.lastIndexOf('/');
            String end;
            if (slash < 0) {
                end = s;
                s = RESOURCE_SUFFIX;
            } else {
                end = s.substring(slash + 1);
                s = s.substring(0, slash + 1) + RESOURCE_SUFFIX;
            }
            boolean absolute = s.startsWith("/");
            if (absolute) s = s.substring(1);

            s = replaceHelper(s);

            if (absolute) s = "/" + s;
            if (s.indexOf(RESOURCE_SUFFIX) < 0)
              return path;
            s = s.substring(0, s.length() - RESOURCE_SUFFIX.length()) + end;
            if (path.equals(s)) {
               s = mapResourcePath(path);
            }
            pathCache.put(path, s);
        }
        return s;
    }

    /**
     * Example:
     *   Input File: META-INF/native/libnetty_tcnative_linux_x86_64.so
     *   Rule:
     *     Pattern: META-INF.native.libnetty_tcnative*RESOURCE
     *     Result: META-INF.native.libcom_example_shaded_netty_tcnative@1RESOURCE
     *   Renamed File: META-INF/native/libcom_example_shaded_netty_tcnative_linux_x86_64.so
     * Note: input file path in this case will be "META-INF/native/libnetty_tcnative_linux_x86_64RESOURCE" (prefix of
     *   the path, before the first '.' (dot), and plus suffix "RESOURCE"). Therefore, pattern should have a suffix
     *   "RESOURCE". Also, result should have a suffix "RESOURCE": it will be replaced with the suffix from the input.
     */
    private String mapResourcePath(String path) {
        String s = path;
        int firstDot = s.indexOf('.');
        String suffix;
        if (firstDot < 0) {
            suffix = "";
        } else {
            suffix = s.substring(firstDot);
            s = s.substring(0, firstDot) + RESOURCE_SUFFIX;
        }
        boolean absolute = s.startsWith("/");
        if (absolute) s = s.substring(1);

        s = replaceHelper(s);

        if (absolute) s = "/" + s;
        if (!s.endsWith(RESOURCE_SUFFIX)) {
            return path;
        } else {
            return s.substring(0, s.length() - RESOURCE_SUFFIX.length()) + suffix;
        }
    }

    public Object mapValue(Object value) {
        if (value instanceof String) {
            String s = valueCache.get(value);
            if (s == null) {
                s = (String)value;
                if (isArrayForName(s)) {
                    String desc1 = s.replace('.', '/');
                    String desc2 = mapDesc(desc1);
                    if (!desc2.equals(desc1))
                        return desc2.replace('/', '.');
                } else {
                    s = mapPath(s);
                    if (s.equals(value)) {
                        boolean hasDot = s.indexOf('.') >= 0;
                        boolean hasSlash = s.indexOf('/') >= 0;
                        if (!(hasDot && hasSlash)) {
                            if (hasDot) {
                                s = replaceHelper(s.replace('.', '/')).replace('/', '.');
                            } else {
                                s = replaceHelper(s);
                            }
                        }
                    }
                }
                valueCache.put(value, s);
            }
            // TODO: add back class name to verbose message
            if (verbose && !s.equals(value))
                System.err.println("Changed \"" + value + "\" -> \"" + s + "\"");
            return s;
        } else {
            return super.mapValue(value);
        }
    }

    private String replaceHelper(String value) {
        for (Wildcard wildcard : wildcards) {
            String test = wildcard.replace(value);
            if (test != null)
                return test;
        }
        return value;
    }
}
