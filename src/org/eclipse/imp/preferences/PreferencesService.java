/*
 * (C) Copyright IBM Corporation 2007
 * 
 * This file is part of the Eclipse IMP.
 */
package org.eclipse.imp.preferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.framework.Bundle;
import org.osgi.service.prefs.BackingStoreException;

/**
 * A Preferences Service for IMP.  Intended as an adaptation of the
 * Eclipse PreferencesService and built on top of it, with simplifications
 * appropriate to use in IMP.
 * 
 * 
 * @see	org.eclipse.core.internal.PreferencesService.
 * 
 * @author sutton
 *
 */

public class PreferencesService implements IPreferencesService
{

	private IProject project = null;
	private String projectName = null;
	private String languageName = null;
	
	org.eclipse.core.runtime.preferences.IPreferencesService preferencesService = null;
	IEclipsePreferences preferencesRoot = null;
	
	// Scopes at the four standard preference levels
	// All are unique but the project scope
	private ProjectScope projectScope = null;
	private ConfigurationScope configurationScope = new ConfigurationScope();
	private InstanceScope instanceScope = new InstanceScope();
	private DefaultScope defaultScope = new DefaultScope();	
	
	/*
	 * Constructors
	 */
	
	public PreferencesService() {
		getPreferencesServiceAndRoot();
	}

	public PreferencesService(String projectName) {
		this();
		setProjectName(projectName);
	}
	
	public PreferencesService(IProject project) {
		this();
		setProject(project);
	}

	public PreferencesService(IProject project, String languageName) {
		this(project);
		setLanguageName(languageName);
	}
	
	/*
	 * Utilities for constructors and (re)initializations
	 */
	
	private void getPreferencesServiceAndRoot() {
	    preferencesService = Platform.getPreferencesService();
	    preferencesRoot = preferencesService.getRootNode();
	}	
	
	private static IProject getProjectFromName(String name) {
		if (name == null || name.equals("")) return null;
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = workspaceRoot.getProject(name);
		if (project.exists()) return project;
		return null;
	}
	
	
	/*
	 * Methods to get and set project, project name, and language name
	 * @see org.eclipse.imp.preferences.service.IPreferencesService#setLanguageName(java.lang.String)
	 */

	// TODO:  Add (or refine) error checks!
	
	public void setLanguageName(String languageName) {
		this.languageName = languageName;
	}
	
	public String getLanguageName() { return languageName; }
	
	
	public void setProjectName(String projectName) {
		if (projectName == null || projectName.equals("")) {
			System.out.println("PreferencesService.setProjectName:  name is null or empty; clearing project, project name, and project scope");
		}
		if (projectName.equals("")) projectName = null; 
		this.projectName = projectName;
		project = getProjectFromName(projectName);
		if (project != null ) {
			ProjectScope oldProjectScope = projectScope;
				projectScope = new ProjectScope(project);
			toggleProject(oldProjectScope, projectScope, projectName);
		}
		
		else projectScope = null;
	}
	

	private void toggleProject(ProjectScope oldProjectScope, ProjectScope newProjectScope, String projectName)
	{
		ProjectSelectionEvent event = null;	
		if (projectName == null)
			event = new ProjectSelectionEvent(null, null);
		else if (oldProjectScope == null && newProjectScope == null)
			event = new ProjectSelectionEvent(null, null);
		else if (oldProjectScope == null && newProjectScope != null)
			event = new ProjectSelectionEvent(null, newProjectScope.getNode(projectName));
		else if (oldProjectScope != null && newProjectScope == null)
			event = new ProjectSelectionEvent(oldProjectScope.getNode(projectName), null);
		else if (oldProjectScope != null && newProjectScope != null)
			event = new ProjectSelectionEvent(oldProjectScope.getNode(projectName), newProjectScope.getNode(projectName));
		
		// so now event should be defined ...
		
		fireProjectSelectionEvent(event);
		
	}
	
	
	public String getProjectName() { return projectName; }
	
	
	public void setProject(IProject project)
	{
		ProjectScope oldProjectScope = projectScope;
		this.project = project;
		if (project != null) {
			projectName = project.getName();
			//	 oldProjectScope = projectScope;
			projectScope = new ProjectScope(project);
			//toggleProject(oldProjectScope, projectScope, projectName);
		} else {
			//System.out.println("PreferencesService.setProject:  project is null; clearing project, project name, and project scope");
			projectName = null;
			projectScope = null;
		}
		toggleProject(oldProjectScope, projectScope, projectName);
	}
	
	
	public IProject getProject() { return project; }
	
	
	
	/*
	 * Get and set preferences by batch by level
	 */
	
	public IEclipsePreferences getPreferences(String level)
	{
		if (level == null) {
			throw new IllegalArgumentException("PreferencesService.getPreferences:  given level is null");
		}
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException("PreferencesService.getPreferences:  given level is not valid (level = " + level);
		}
		
		IEclipsePreferences preferences = null;
		
		if (level.equals(PROJECT_LEVEL)) {
			if (projectScope != null && languageName != null) {
				preferences = projectScope.getNode(languageName);
			}
		} else if (level.equals(INSTANCE_LEVEL)) {
			preferences = instanceScope.getNode(languageName);	
		} else if (level.equals(CONFIGURATION_LEVEL)) {
			preferences = configurationScope.getNode(languageName);
		} else if (level.equals(DEFAULT_LEVEL)) {
			preferences = defaultScope.getNode(languageName);
		}

		return preferences;
	}
	
	
	/**
	 * Set the preferences at the given level to the given new preferences,
	 * clearing any existing preferences.  If the new preferences are null,
	 * just clear the existing preferences.
	 * 
	 * @param level				the level at which preferences are to be set; must be
	 * 							one of the values defined in IPreferencesService
	 * @param newPreferences	the new preferences to be set at the given level (or
	 * 							null if the existing preferences are just to be cleared)
	 * @throws IllegalArgumentException
	 * 							if the given level is null or not one of the values
	 * 							defined in IPreferencesService
	 */
	public void setPreferences(String level, IEclipsePreferences newPreferences)
	{
		if (level == null) {
			throw new IllegalArgumentException(
				"PreferencesService.getPreferences:  given level is null");
		}
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException(
				"PreferencesService.getPreferences:  given level is not valid (level = " + level);
		}

		IEclipsePreferences node = clearPreferencesAtLevel(level);
		
		if (newPreferences == null) return;
		
		String[] keys = null;
		try {
			keys = newPreferences.keys();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setPreferences():  " +
				"BackingStoreException getting keys; returning (note:  preferences for this node may have been cleared)");
			return;
		}
		for (int i = 0; i < keys.length; i++) {
			// Assume that if we get values out they're legal, and
			// and that if they're legal we can safely put them in
			String val = newPreferences.get(keys[i], null);
			node.put(keys[i], val);
		}
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setPreferences():  " +
				"BackingStoreException flushing new preferences; returning (note:  preferences for this node may have been cleared and not set)");
			return;
		}

	}
	
	
	/*
	 * Documentatry interlude:
	 * 
	 * The methods to get an applicable preference by type make use of the corresponding
	 * type-specific "get" methods in the Eclipse PreferencesService.  Those methods take
	 * a qualifier (i.e., that designates a name space), a key, a default value, and
	 * a (possibly empty) list of scope contexts to be checked ahead of the standard
	 * contexts that are automatically checked in the standard order (project, instance,
	 * configuration, and default).
	 * 
	 * Recall that, while the PreferencesService allows preference values to be gotten,
	 * it provides no way to set preference values.  The setting of preference values
	 * has to be done through preference nodes that are associated with scope contexts.
	 * 
	 * We would like to be able to use a single qualifier to get the preferences for
	 * a particular language (e.g., qualifier "X10" for language X10).  When we set
	 * a preference at a level, we can provide just such a simple qualifier since
	 * we know the language name, e.g., in
	 * 		setIntPreference(String level, String key, int value)
	 * we can get the scope for the level, get the preferences node for the language-
	 * name qualifier, and set the value in the node.  That's simple.  If the level
	 * is (for example) "instance", then the path to the target preference node in the
	 * preferences model is
	 * 		/root/instance/<languageName>
	 * but if the level is "project" then the path to the target preference node is
	 * 		/root/project/<projectName>/<languageName>
	 * However, when you retrieve the preference from the Eclipse PreferencesService
	 * using the language name as qualifier and the appropriate key, the service
	 * will search on the project level for
	 * 		/root/project/<languageName>
	 * In other words, the project is not taken into account by the service and that
	 * level is ignored in the preferences model.
	 * 
	 * The Eclipse PreferencesService can accommodate project names as part of the
	 * qualifier.  So, given a preference stored in the project node as above, it
	 * can be retrieved using <projectName>/<languageName> as the qualifer.  This leads
	 * the service to search the preferences model along the path
	 * 	 	root/project/<projectName>/<languageName>
	 * where it will be found.  However, if you add the project name to the language
	 * name in the qualifier on the other preferences levels, that will lead to
	 * searching on paths like
	 * 		/root/instance/<projectName>/<languageName>
	 * which is not how the preferences would have been stored on the other levels
	 * if the qualifier was just the language name.
	 * 
	 * It's possible to use the project name as part of the qualifier at the instance,
	 * configuration, and default levels.  That would allow the same qualifier to be
	 * used at all levels.  However, that would result, in effect, in project-specific
	 * preferences at levels that are supposed to be project independent.  It would
	 * also complicate the setting of preferences, which, at the project-independent
	 * levels, would have to be done consistently for each project where a language
	 * is used rather than just once for all projects where a language is used.
	 * 
	 * Alternatively, it's possible to use different qualifiers at the projec level
	 * and project-independent levels, but that complicates the use of the preferences
	 * service, requiring clients to query for preferences using different qualifiers
	 * in different cases.
	 * 
	 * To keep things simple for clients, I've taken advantage of the ability to
	 * provide the Eclipse PreferencesService with scopes to be searched ahead of
	 * the standard scopes.  That is, each of the type-specific "get" methods defined
	 * here calls the corresponding Eclipse-service get methdod, providing it with
	 * the project-specific scope in which the simple language-name qualifier will
	 * be recognized.  Users can thus use the language name as a qualifier that will
	 * be meaningful across all scope levels.  They will not have to worry about whether
	 * different qualifiers are needed on different levels and whether the project name
	 * should or should not be part of a qualifier.
	 */

	
	/*
	 * Get applicable preferences by type
	 */	

	private IScopeContext[] specificProjectScope() {
		if (projectScope == null) return new IScopeContext[0];
		return new IScopeContext[] { getScopeForLevel(IPreferencesService.PROJECT_LEVEL)};
	}
	
	
	public boolean getBooleanPreference(String key) {
		return preferencesService.getBoolean(languageName, key, false, specificProjectScope());
	}
	
	/*
	 * The implementation of the "get" method for byte arrays is more complicated
	 * than for other types due to the way in which the preferences nodes encode
	 * byte arrays while the preferences service ignores encoding.
	 * 
	 * Things considered in this implementation:
	 * - Byte arrays are encoded when written to a preferences node and
	 *   decoded when read from the node but not from the Eclipse Preferences
	 *   Service; that is addressed here by getting the value from the
	 *   preferences node rather than from the preferences service
	 * - The IMP Preferences Service and the Eclipse Preferences Service
	 *   both have the same four standard levels and these are by default
	 *   in the same standard order
	 * - But the Eclipse levels may have been reordered (although I'm not
	 *   doing that), so this implementation does not assume that they're
	 *   in the same order
	 * - The levels are named in different ways in the two services, e.g.,
	 *   "project" in Eclipse and "PROJECT_LEVEL" in IMP, so that has to
	 *   be accommodated
	 * Things not considered:
	 * - Additional levels that may have been added to the preferences model
	 *   (but I'm certainly not adding any!)
	 *
	 * @see org.eclipse.imp.preferences.service.IPreferencesService#getByteArrayPreference(java.lang.String)
	 */
	public byte[] getByteArrayPreference(String key)
	{
		byte[] result = null;
		String[] lookupOrder = preferencesService.getLookupOrder(languageName, key);
		String[] levels = IPreferencesService.levels;
		outer:  for (int i = 0; i < lookupOrder.length; i++) {
			for (int j = 0; j < levels.length; j++) {
				String level = null;
				if (levels[j].startsWith(lookupOrder[i].toUpperCase())) {
					level = levels[j];
					IScopeContext context = getScopeForLevel(level);
					IEclipsePreferences node = context.getNode(languageName);
					result = node.getByteArray(key, new byte[0]);
					if (result.length == 0) continue;
					break outer;
				}
			}
		}
		return result;
	}
	
	public double getDoublePreference(String key) {
		return preferencesService.getDouble(languageName, key, 0, specificProjectScope());
	}
	
	public float getFloatPreference(String key) {
		return preferencesService.getFloat(languageName, key, 0, specificProjectScope());
	}
		
	public int getIntPreference(String key) {
		return preferencesService.getInt(languageName, key, 0, specificProjectScope());
	}
	
	public long getLongPreference(String key) {
		return preferencesService.getLong(languageName, key, 0, specificProjectScope());
	}
	
	public String getStringPreference(String key) {
		String result = preferencesService.getString(languageName, key, null, specificProjectScope());
		//if (result.equals("")) {
		//	result = null;
		//}
		return performSubstitutions(result);
	}
	

	public String getRawStringPreference(String key) {
		String result = preferencesService.getString(languageName, key, null, specificProjectScope());
		return result;
	}
	
	/*
	 * Get applicable preferences by project and type
	 * 
	 * Requests are evaluated relative to an implicit "current language."
	 * It is an error to request a preference when the current langauge is
	 * not defined, because all preferences are assumed to be defined relative
	 * to some language.
	 * 
	 * Requests are evaluated relative to a given project, regardless of
	 * whether there is a "current project" defined.
	 * 
	 * Preferences are returned from the lowest level at which set.  If no
	 * preference is defined for a given key at all levels, then an apporpriate
	 * default value is returned.  This is 0 for the numeric preferences,
	 * false for boolean preferences, the empty string for String preferences,
	 * and the empty array for byte[].
	 */
	
	public boolean getBooleanPreference(IProject project, String key) 
	{
		return preferencesService.getBoolean(languageName, key, false, new IScopeContext[] { new ProjectScope(project) } );
	}
	
	
	/*
	 * This is more complicated than retrieving values for the other types because of
	 * the encoding that is done (or not done) differently between the preferences service
	 * and preferences nodes.  See the version of getByteArrayPreference(..) that only takes
	 * a key for more detailed comments.
	 * 
	 * @see org.eclipse.imp.preferences.IPreferencesService#getByteArrayPreference(org.eclipse.core.resources.IProject, java.lang.String)
	 */
	public byte[]  getByteArrayPreference(IProject project, String key)
	{
		byte[] result = null;
		String[] lookupOrder = preferencesService.getLookupOrder(languageName, key);
		String[] levels = IPreferencesService.levels;
		outer:  for (int i = 0; i < lookupOrder.length; i++) {
			for (int j = 0; j < levels.length; j++) {
				String level = null;
				if (levels[j].startsWith(lookupOrder[i].toUpperCase())) {
					level = levels[j];
					IScopeContext context = null;
					if (level.equals(PROJECT_LEVEL)) {
						context = getScopeForProject(project);
					} else {
						context = getScopeForLevel(level);
					}
					IEclipsePreferences node = context.getNode(languageName);
					result = node.getByteArray(key, new byte[0]);
					if (result.length == 0) continue;
					break outer;
				}
			}
		}
		return result;
	}

	
	public double getDoublePreference(IProject project, String key) {
		return preferencesService.getDouble(languageName, key, 0, new IScopeContext[] { new ProjectScope(project) } );
	}
	
	public float getFloatPreference(IProject project, String key) {
		return preferencesService.getFloat(languageName, key, 0,  new IScopeContext[] { new ProjectScope(project) });
	}
		
	public int getIntPreference(IProject project, String key) {
		return preferencesService.getInt(languageName, key, 0, new IScopeContext[] { new ProjectScope(project) });
	}
	
	public long getLongPreference(IProject project, String key) {
		return preferencesService.getLong(languageName, key, 0, new IScopeContext[] { new ProjectScope(project) } );
	}
	
	public String getRawStringPreference(IProject project, String key) {
		return preferencesService.getString(languageName, key, null, new IScopeContext[] { new ProjectScope(project) } );
	}

        public String getStringPreference(IProject project, String key) {
            String value= getRawStringPreference(project, key);
            String result= performSubstitutions(value, project);

            return result;
        }
        
        /**
         * An evaluator for non-parameterized (constant) references.
         */
        private interface ConstantEvaluator {
            String getValue();
        }

        private static final Map<String,ConstantEvaluator> sConstantMap= new HashMap<String, ConstantEvaluator>();

        static {
            sConstantMap.put("workspaceLoc", new ConstantEvaluator() {
                public String getValue() {
                    return ResourcesPlugin.getWorkspace().getRoot().getLocation().toPortableString();
                }
            });
            sConstantMap.put("os", new ConstantEvaluator() {
                public String getValue() {
                    return Platform.getOS();
                }
            });
            sConstantMap.put("arch", new ConstantEvaluator() {
                public String getValue() {
                    return Platform.getOSArch();
                }
            });
            sConstantMap.put("nl", new ConstantEvaluator() {
                public String getValue() {
                    return Platform.getNL();
                }
            });
            sConstantMap.put("ws", new ConstantEvaluator() {
                public String getValue() {
                    return Platform.getWS();
                }
            });
        }

        /**
         * An evaluator for parameterized value references.
         */
        private interface ParamEvaluator {
            String getValue(String param);
        }

        private static final Map<String,ParamEvaluator> sParamMap= new HashMap<String, ParamEvaluator>();

        static {
            sParamMap.put("pluginLoc", new ParamEvaluator() {
                public String getValue(String pluginID) {
                    Bundle bundle= Platform.getBundle(pluginID);
                    if (bundle == null) {
                        return "<no such plugin: " + pluginID + ">";
                    }
                    try {
                        String bundleLoc= FileLocator.toFileURL(bundle.getEntry("")).getFile();
                        return bundleLoc;
                    } catch (IOException e) {
                        return "<error determining location of plugin: " + pluginID + ">";
                    }
//                  Bundle[] fragments= Platform.getFragments(bundle);
                }
            });
            sParamMap.put("pluginVersion", new ParamEvaluator() {
                public String getValue(String pluginID) {
                    Bundle bundle= Platform.getBundle(pluginID);
                    if (bundle == null) {
                        return "<no such plugin: " + pluginID + ">";
                    }
                    return (String) bundle.getHeaders().get("Bundle-Version");
                }
            });
            sParamMap.put("projectLoc", new ParamEvaluator() {
                public String getValue(String projectName) {
                    IProject project= getProjectFromName(projectName);
                    if (project == null) {
                        return "<no such project: " + projectName + ">";
                    }
                    return project.getLocation().toPortableString();
                }
            });
        }

        private static final Pattern sSimpleSubstRegexp= Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_]*)\\}");
        private static final Pattern sParamSubstRegexp= Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9_]*):([^}]+)\\}");

        public String performSubstitutions(String value) {
            return performSubstitutions(value, null);
        }

        public String performSubstitutions(String value, IProject project) {
            if (value == null) {
                return value;
            }
            do {
                Matcher pm= sParamSubstRegexp.matcher(value);
                if (pm.find(0)) {
                    String id= pm.group(1);
                    String param= pm.group(2);

                    ParamEvaluator e= sParamMap.get(id);
                    String prefValue= (e != null) ? e.getValue(param) : ("<no such preference: " + id + ">");

                    value= value.substring(0, pm.start()) + prefValue + value.substring(pm.end());
                } else {
                    Matcher sm= sSimpleSubstRegexp.matcher(value);
                    if (sm.find(0)) {
                        String id= sm.group(1);
                        String prefValue;
                        if (sConstantMap.containsKey(id)) {
                            prefValue= sConstantMap.get(id).getValue();
                        } else {
                            prefValue= (project != null) ? getStringPreference(project, id) : getStringPreference(id);
                        }
                        value= value.substring(0, sm.start()) + prefValue + value.substring(sm.end());
                    } else {
                        break;
                    }
                }
            } while (true);
            return value;
        }
	
	/*	
	 * Get preferences at a given level by type
	 */

	// SMS 19 Sep 2006:  Are these all wrong????
	
	public boolean getBooleanPreference(String level, String key) {
		//return preferencesService.getBoolean(languageName, key, false, new IScopeContext[] { getScopeForLevel(level)} 	);
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		boolean result = node.getBoolean(key, false);
		return result;
	}
	
	public byte[] getByteArrayPreference(String level, String key) {
		// SMS 19 Sep 2006:  The following comment is now somewhat outdated in that
		// direct access to the preferences nodes is used in all of the methods in this
		// group, but the comment may still be informative with respect to particular
		// issues that affect byte arrays.
		//
		// Why do the following instead of getting the value directly from the preferences service
		// as is done with the other preferences types?  Note that, while values are retrieved here
		// from the preferences service, values are set through the preferences nodes for the individual
		// scope contexts.  (That is because the preferences service allows preferences to be retrieved
		// directly but not set directly.)  Also recall that all preferences are stored interanlly as
		// Strings.  When a byte array is set or retrieved through a preferences context, the byte
		// array is encoded as a Base64 String.  However, when a byte array is retrieved from the
		// preferences service, it is just converted to a string in the "native" representation
		// (that is, without regard to any specific encoding).  Apparently the native encoding is not
		// always Base64.  Consequently, a byte array may be stored through the preferences node in
		// one representation retrieved from the preferences service in another representation, so
		// from the client's perspective the values stored and retrieved may be different.  Since we
		// elsewhere store byte array preferences through the node we retrieve them here from the node.
		IScopeContext context = getScopeForLevel(level);
		IEclipsePreferences node = context.getNode(languageName);
		byte[] result = node.getByteArray(key, new byte[0]);
		return result;
	}
	
	public double getDoublePreference(String level, String key) {
		//return preferencesService.getDouble(languageName, key, 0, new IScopeContext[] { getScopeForLevel(level)} );
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		double result = node.getDouble(key, 0);
		return result;
	}
	
	public float getFloatPreference(String level, String key) {
		//return preferencesService.getFloat(languageName, key, 0, new IScopeContext[] { getScopeForLevel(level)} );
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		float result = node.getFloat(key, 0);
		return result;
	}
	
	public int getIntPreference(String level, String key) {
		//return preferencesService.getInt(languageName, key, 0, new IScopeContext[] { getScopeForLevel(level)} );
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		int result = node.getInt(key, 0);
		return result;
	}
	
	public long getLongPreference(String level, String key) {
		//return preferencesService.getLong(languageName, key, 0, new IScopeContext[] { getScopeForLevel(level)} );
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		long result = node.getLong(key, 0);
		return result;
	}
	
	public String getStringPreference(String level, String key) {
		/*
		if (!isaPreferencesLevel(level)) return null;
		String[] previousLevels = preferencesService.getDefaultLookupOrder(languageName, key);
		String[] newLevels = new String[4];
		for (int i = 0; i < newLevels.length; i++) {
			newLevels[i] = level;
		}
		preferencesService.setDefaultLookupOrder(languageName, key, newLevels);
		String result = preferencesService.getString(languageName, key, null, new IScopeContext[] { getScopeForLevel(level)} );
		preferencesService.setDefaultLookupOrder(languageName, key, previousLevels);
		// SMS 8 Sep 2006:  leftover from debugging
		if (result != null && result.equals("")) {
			result = null;
			result = "";
		}
		return result;
		*/
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		String result = node.get(key, null);
		return performSubstitutions(result);
	}
	
	
	/*
     *  Return the "raw" (unsubstituted) version of the preference value.
     *  Needed at least by field editors that may want to inherit a raw
     *  value from a higher level.
     *  SMS 21 Feb 2008
     */
	public String getRawStringPreference(String level, String key) {
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		String result = node.get(key, null);
		return result;
	}
	
	
	
	/*
	 * Get preferences for a given project by type
	 * 
	 * Requests are evaluated relative to an implicit "current language."	
	 * It is an error to request a preference when the current langauge is
	 * not defined, because all preferences are assumed to be defined relative
	 * to some language.
	 * 
	 * Requests are evaluated relative to the given project only.  If a
	 * preference for the given key is defined for that project, then the
	 * associated value is returned.  If no preferences is defined
	 * for the given key in the given project, then a suitable default value
	 * is returned.  This is 0 for the numeric preferences, false for boolean
	 * preferences, the null string for String preferences, and the empty
	 * array for byte[].
	 */
	public boolean getBooleanPreferenceForProject(IProject project, String key) {
		IScopeContext scope = getScopeForProject(project);
		IEclipsePreferences node = scope.getNode(languageName);
		boolean result = node.getBoolean(key, false);
		return result;
	}
	
	
	public byte[]  getByteArrayPreferenceForProject(IProject project, String key) {
		IScopeContext scope = getScopeForProject(project);
		IEclipsePreferences node = scope.getNode(languageName);
		byte[] result = node.getByteArray(key, new byte[0]);
		return result;
	}
	
	
	public double  getDoublePreferenceForProject(IProject project, String key) {
		IScopeContext scope = getScopeForProject(project);
		IEclipsePreferences node = scope.getNode(languageName);
		double result = node.getDouble(key, 0);
		return result;
	}
	
	
	public float getFloatPreferenceForProject(IProject project, String key) {
		IScopeContext scope = getScopeForProject(project);
		IEclipsePreferences node = scope.getNode(languageName);
		float result = node.getFloat(key, 0);
		return result;
	}
	
	
	public int getIntPreferenceForProject(IProject project, String key) {
		IScopeContext scope = getScopeForProject(project);
		IEclipsePreferences node = scope.getNode(languageName);
		int result = node.getInt(key, 0);
		return result;
	}
	
	
	public long getLongPreferenceForProject(IProject project, String key) {
		IScopeContext scope = getScopeForProject(project);
		IEclipsePreferences node = scope.getNode(languageName);
		long result = node.getLong(key, 0);
		return result;
	}	
		
	
	public String  getStringPreferenceForProject(IProject project, String key) {
		IScopeContext scope = getScopeForProject(project);
		IEclipsePreferences node = scope.getNode(languageName);
		String result = node.get(key, null);
		return performSubstitutions(result, project);
	}	
	
	
	
	public String  getRawStringPreferenceForProject(IProject project, String key) {
		IScopeContext scope = getScopeForProject(project);
		IEclipsePreferences node = scope.getNode(languageName);
		String result = node.get(key, null);
		return result;
	}	
	
	
	
	/*
	 * Set preferences at a given level by type
	 */
	
	public void setBooleanPreference(String level, String key, boolean value) {
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		node.putBoolean(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setByteArrayPreference(String, String, boolean):  BackingStoreException");
			System.out.println("\tlevel = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	public void setByteArrayPreference(String level, String key, byte[] value) {
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		node.putByteArray(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setByteArrayPreference(String, String, byte[]):  BackingStoreException");
			System.out.println("\tlevel = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	public void setDoublePreference(String level, String key, double value) {
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		node.putDouble(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setDoublePreference(String, String, double):  BackingStoreException");
			System.out.println("\tlevel = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	public void setFloatPreference(String level, String key, float value) {
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		node.putFloat(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setFloatPreference(String, String, float):  BackingStoreException");
			System.out.println("\tlevel = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	public void setIntPreference(String level, String key, int value) {
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		node.putInt(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setIntPreference(String, String, int):  BackingStoreException");
			System.out.println("\tlevel = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	public void setLongPreference(String level, String key, long value) {
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		node.putLong(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.set	LongPreference(String, String, long):  BackingStoreException");
			System.out.println("\tlevel = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	public void setStringPreference(String level, String key, String value) {
		IScopeContext scope = getScopeForLevel(level);
		IEclipsePreferences node = scope.getNode(languageName);
		node.put(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setStringPreference(String, String, String):  BackingStoreException");
			System.out.println("\tlevel = " + level + "; key = " + key + "; value = " + value);
		}
	}
		
	
	
	/*
	 * Get preferences for a given level, language, and project by type
	 */

	public boolean getBooleanPreference(String languageName, String projectName, String level, String key, boolean def)
	{
		IEclipsePreferences node = getNodeForParameters("getBooleanPreference", languageName, level, projectName, key);	
		return node.getBoolean(key, def);
	}
	

	public byte[]  getByteArrayPreference(String languageName, String projectName, String level, String key, byte[] def)
	{
		IEclipsePreferences node = getNodeForParameters("getByteArrayPreference", languageName, level, projectName, key);	
		return node.getByteArray(key, def);
	}
	
	
	public double getDoublePreference(String languageName, String projectName, String level, String key, double def)
	{
		IEclipsePreferences node = getNodeForParameters("getDoublePreference", languageName, level, projectName, key);	
		return node.getDouble(key, def);
	}
	
	
	public float   getFloatPreference(String languageName, String projectName, String level, String key, float def)
	{
		IEclipsePreferences node = getNodeForParameters("getFloatPreference", languageName, level, projectName, key);	
		return node.getFloat(key, def);
	}
	
	
	public int getIntPreference(String languageName, String projectName, String level, String key, int def)
	{
		IEclipsePreferences node = getNodeForParameters("getIntPreference", languageName, level, projectName, key);	
		return node.getInt(key, def);
	}
	
	public long getLongPreference(String languageName, String projectName, String level, String key, long def)
	{
		IEclipsePreferences node = getNodeForParameters("getLongPreference", languageName, level, projectName, key);	
		return node.getLong(key, def);
	}

	public String  getStringPreference(String languageName, String projectName, String level, String key, String def)
	{
		IEclipsePreferences node = getNodeForParameters("getStringPreference", languageName, level, projectName, key);	
		return performSubstitutions(node.get(key, def), getProjectFromName(projectName));
	}

	
	public String  getRawStringPreference(String languageName, String projectName, String level, String key, String def)
	{
		IEclipsePreferences node = getNodeForParameters("getRawStringPreference", languageName, level, projectName, key);	
		return node.get(key, def);
	}
	
	
	
	
	
	/*
	 * Set preferences for a given level, language, and project by type
	 */
	
	public void setBooleanPreference(String languageName, String projectName, String level, String key, boolean value)
	{
		IEclipsePreferences node = getNodeForParameters("setBooleanPreference", languageName, level, projectName, key);
		node.putBoolean(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setBooleanPreference(String, String, String, String, boolean):  BackingStoreException");
			System.out.println("\tlanguage = " + languageName + "; project = " + projectName + "; level = " + level + "; key = " + key + "; value = " + value);
		}
	}		
	
	public void setByteArrayPreference(String languageName, String projectName, String level, String key, byte[] value)
	{
		IEclipsePreferences node = getNodeForParameters("setByteArrayPreference", languageName, level, projectName, key);
		node.putByteArray(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setByteArrayPreference(String, String, String, String, byte[]):  BackingStoreException");
			System.out.println("\tlanguage = " + languageName + "; project = " + projectName + "; level = " + level + "; key = " + key + "; value = " + value);
		}
	}	
	
	public void setDoublePreference(String languageName, String projectName, String level, String key, double value)
	{
		IEclipsePreferences node = getNodeForParameters("setDoublePreference", languageName, level, projectName, key);
		node.putDouble(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setDoublePreference(String, String, String, String, double):  BackingStoreException");
			System.out.println("\tlanguage = " + languageName + "; project = " + projectName + "; level = " + level + "; key = " + key + "; value = " + value);
		}
	}	
	
	public void setFloatPreference(String languageName, String projectName, String level, String key, float value)
	{
		IEclipsePreferences node = getNodeForParameters("setFloatPreference", languageName, level, projectName, key);
		node.putFloat(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setFloatPreference(String, String, String, String, float):  BackingStoreException");
			System.out.println("\tlanguage = " + languageName + "; project = " + projectName + "; level = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	public void setIntPreference(String languageName, String projectName, String level, String key, int value)
	{
		IEclipsePreferences node = getNodeForParameters("setIntPreference", languageName, level, projectName, key);
		node.putInt(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setIntPreference(String, String, String, String, int):  BackingStoreException");
			System.out.println("\tlanguage = " + languageName + "; project = " + projectName + "; level = " + level + "; key = " + key + "; value = " + value);
		}
	}
		
	public void setLongPreference(String languageName, String projectName, String level, String key, long value)
	{
		IEclipsePreferences node = getNodeForParameters("setLongPreference", languageName, level, projectName, key);
		node.putLong(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setLongPreference(String, String, String, String, long):  BackingStoreException");
			System.out.println("\tlanguage = " + languageName + "; project = " + projectName + "; level = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	public void setStringPreference(String languageName, String projectName, String level, String key, String value)
	{
		IEclipsePreferences node = getNodeForParameters("setStringPreference", languageName, level, projectName, key);	
		node.put(key, value);
		try {
			node.flush();
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setStringPreference(String, String, String, String, String):  BackingStoreException");
			System.out.println("\tlanguage = " + languageName + "; project = " + projectName + "; level = " + level + "; key = " + key + "; value = " + value);
		}
	}
	
	
	
	public IScopeContext getPreferencesScope(String level, IProject proj)
	{
		if (level.equals(PreferencesService.CONFIGURATION_LEVEL)) {
			return configurationScope;
		} else if (level.equals(PreferencesService.INSTANCE_LEVEL)) {
			return instanceScope;
		} else if (level.equals(PreferencesService.DEFAULT_LEVEL)) {
			return defaultScope;
		} else if (level.equals(PreferencesService.PROJECT_LEVEL)) {
			return new ProjectScope(proj);
		}
		return null;
	}
	
	
	public void validateParameters(String methodName, String languageName, String level, String projectName, String key)
	{
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException(
				"PreferencesService." + methodName + ":  given level is not a valid preferences level:  " + level);
		}
		if (languageName == null || languageName.equals("")) {
			throw new IllegalArgumentException(
				"PreferencesService." + methodName + ":  given language name is null or empty");
		}
		if (level.equals(PreferencesService.PROJECT_LEVEL)) {
				if ((projectName == null) || projectName.equals("")) {
					throw new IllegalArgumentException(
						"PreferencesService." + methodName + ":  level is 'project' but project name is null or empty");
				}
				IProject proj = getProjectFromName(projectName);
				if (proj == null) {
					throw new IllegalArgumentException(
						"PreferencesService." + methodName +
						":  level is 'project' but project name '" + projectName + "' does not denote an existing project");
				}
		} 
		if (key == null || key.equals("")) {
			throw new IllegalArgumentException(
				"PreferencesService.setStringPreference:  key is null or empty");
		}
	}
	
	
	public IEclipsePreferences getNodeForParameters(String methodName, String languageName, String level, String projectName, String key)
	{
		validateParameters(methodName, languageName, level, projectName, key);
		
		IProject proj = getProjectFromName(projectName);
		IScopeContext scope = getPreferencesScope(level, proj);
		if (scope == null) {
			throw new IllegalStateException(
					"PreferencesService." + methodName + ":  unable to obtain valid preferences scope");
		}

		IEclipsePreferences node = scope.getNode(languageName);
		if (node == null) {
			throw new IllegalStateException(
					"PreferencesService." + methodName + ":  unable to obtain valid preferences node");
		}

		return node;
	}
	
	
	/*
	 * Clear preferences at a given level
	 */
	
	
	/**
	 * Clear all of the preferences at a given level.
	 * 
	 * @return	the (cleared) preferences node for the given level
	 */
	public IEclipsePreferences clearPreferencesAtLevel(String level) {
		return clearPreferencesAtLevel(project, level);	
	}
	
	/**
	 * Clear all of the preferences at a given level, considering the
	 * given project if the level is the project level.
	 * 
	 * @return	the (cleared) preferences node for the given level
	 */
	public IEclipsePreferences clearPreferencesAtLevel(IProject project, String level)
	{
		if (level == null) {
			throw new IllegalArgumentException(
				"PreferencesService.clearPreferencesAtLevel (with project):  given level is null");
		}
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException(
				"PreferencesService.clearPreferencesAtLevel (with project):  given level '" + level + "' is not a valid level");
		}
		if (project == null && level.equals(PROJECT_LEVEL)) {
			//throw new IllegalStateException(
			//	"PreferencesService.clearPreferencesAtLevel (with project): given project is null when given level is 'project'");
			return null;
		}

		IEclipsePreferences node = null;
		try {
			if (level.equals(PROJECT_LEVEL) && projectScope != null) {
				node = projectScope.getNode(languageName);
			} else if (level.equals(INSTANCE_LEVEL)) {
				node = instanceScope.getNode(languageName);	
			} else if (level.equals(CONFIGURATION_LEVEL)) {
				node = configurationScope.getNode(languageName);
			} else if (level.equals(DEFAULT_LEVEL)) {
				node = defaultScope.getNode(languageName);
			}
			if (node != null) {
				//node.clear();
				String[] keys = node.keys();
				for (int i = 0; i < keys.length; i++) {
					node.remove(keys[i]);
				}
				node.flush();	// SMS 28 Nov 2006
			}
		} catch (BackingStoreException e) {
			System.out.println("PreferencesService.setPreferences():  " +
				"BackingStoreException clearing existing preferences; attempting to add new preferences anyway");
		}
		
		return node;
	}

	
	/**
	 * Clear the specific preference associated with a given key at
	 * a given level.
	 * 
	 * @return	the cleared value (may be null)
	 */
	public String clearPreferenceAtLevel(String level, String key)
	{
		return clearPreferenceAtLevel(project, level, key);
	}
	

	/**
	 * Clear the specific preference associated with a given key at
	 * a given level.  Consider the given project if the given level
	 * is the project level.
	 * 
	 * @return	the cleared value (may be null)
	 */
	public String clearPreferenceAtLevel(IProject project, String level, String key)
	{
		if (level == null) {
			throw new IllegalArgumentException(
				"PreferencesService.clearPreferenceAtLevel (with project):  given level is null");
		}
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException(
				"PreferencesService.clearPreferenceAtLevel (with project):  given level '" + level + "' is not a valid level");
		}
		if (project == null && level.equals(PROJECT_LEVEL)) {
			throw new IllegalStateException(
				"PreferencesService.clearPreferenceAtLevel (with project): given project is null when given level is 'project'");
		}

		IScopeContext context = null;
		if (level.equals(PROJECT_LEVEL)) {
			context = getScopeForProject(project);
		} else {
			context = getScopeForLevel(level);
		}
		IEclipsePreferences node = context.getNode(languageName);
		if (node == null) return null;
		String preference = node.get(key, null);
		node.remove(key);
		try {
			node.flush();
		} catch (BackingStoreException e){
			System.err.println("PreferencesService.clearPreferenceAtLevel(..):  BackingStoreException trying to flush node with cleared preference;\n" +
				"\tproject = " + project + "; level = " + level + "; key = " + key + "\n" +	
				"\tclearing may not have a persistent effect");
		}
		return preference;
	}
	
	
	
	/*
	 * Other useful things
	 */
	
	/**
	 * The standard search order of preference levels in the IMP preferences
	 * service.  This is the default search order in the Eclipse PreferencesService
	 * and it is presumed not to change during the operation of the IMP service.
	 *  
	 */
	public final static String[] levels = { PROJECT_LEVEL, INSTANCE_LEVEL, CONFIGURATION_LEVEL, DEFAULT_LEVEL };
	

	
	public int indexForLevel(String levelName) {
		for (int i = 0; i < levels.length; i++) {
			if (levels[i].equals(levelName))
				return i;	
		}
		return -1;
	}
	
	
	
	
	/**
	 * Returns the first preference level in the standard search order
	 * at which the given key is defined for the current language.
	 * Returns null if the given key is not defined.
	 */
	public String getApplicableLevel(String key, String level)
	{
		return getApplicableLevel(project, key, level);
	}
	
	/**
	 * Returns the first preference level in the standard search order
	 * at which the given key is defined for the current language.
	 * Returns null if the given key is not defined.
	 */
	public String getApplicableLevel(IProject project, String key, String level)
	{
		// Level == null okay, as we can start at the bottom in that case
		
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException("PreferencesService.getApplicableLevel (with project):  given level '" + level + "' is not a real level");
		}
		if (key == null) {
			throw new IllegalArgumentException("PreferencesService.getApplicableLevel (with project):  given key is null");
		}
		if (level.equals(PROJECT_LEVEL) && projectScope == null) {
			throw new IllegalStateException("PreferencesService.getApplicableLevel (with project):  node for project requested when project scope is null");
		}
		

		int startIndex = level == null ? 0 : getIndexForLevel(level);

		if (startIndex == PROJECT_INDEX) {
			IScopeContext context = getScopeForProject(project);
			IEclipsePreferences node = context.getNode(languageName);
			String[] keys;
			try {
				keys = node.keys();
			} catch (BackingStoreException e) {
				keys = new String[0];
			}
			for (int i = 0; i < keys.length; i++) {
				if (keys[i].equals(key)) return PROJECT_LEVEL;
			}
			startIndex++;
		}
		
		for (int i = startIndex; i < levels.length; i++) {
			// Since all preference values are stored as strings,
			// regardless of attributed base type, I believe that
			// all can be safely retrieved as strings, so long as
			// we don't care about their actual values
			String pref = getStringPreference(levels[i], key);
			if (pref == null) continue;
			return levels[i];
		}
		return null;
	}

	
	public boolean isDefault(String key, String level) {
		return isDefault(project, key, level);
	}
	
	
	public boolean isDefault(IProject project, String key, String level) {
		return DEFAULT_LEVEL.equals(getApplicableLevel(project, key, level));
	}
	
	
	public boolean isDefined(String key) {
		return isDefined(project, key);
	}
		
	public boolean isDefined(IProject project, String key) {
		return getApplicableLevel(project, key, null) == null;
	}
	
	
	
	public boolean isaPreferencesLevel(String possibleLevel) {
		for (int i = 0; i < levels.length; i++) {
			if (levels[i].equals(possibleLevel)) return true;
		}
		return false;
	}
	
	
	public IEclipsePreferences getRootNode() {
		return preferencesService.getRootNode();
	}
	
	public IEclipsePreferences getNodeForLevel(String level)
	{
		if (level == null) {
			throw new IllegalArgumentException("PreferencesService.getNodeForLevel:  given level is null");
		}
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException("PreferencesService.getNodeForLevel:  given level '" + level + "' is not a real level");
		}
		if (level.equals(PROJECT_LEVEL) && projectScope == null) {
			throw new IllegalStateException("PreferencesService.getNodeForLevel:  node for project requested when project scope is null");
		}
		
		IEclipsePreferences node = null;

		if (level.equals(PROJECT_LEVEL)) {
			node = projectScope.getNode(languageName);
		} else if (level.equals(INSTANCE_LEVEL)) {
			node = instanceScope.getNode(languageName);	
		} else if (level.equals(CONFIGURATION_LEVEL)) {
			node = configurationScope.getNode(languageName);
		} else if (level.equals(DEFAULT_LEVEL)) {
			node = defaultScope.getNode(languageName);
		}
		return node;
	}
		
	
	public IEclipsePreferences[] getNodesForLevels()
	{
		if (languageName == null || languageName.equals("")) {
			throw new IllegalStateException("PreferencesService.getNodesForLevels:  language name is invalid (null or empty); no associated preferences nodes");
		}
		IEclipsePreferences[] nodes = new IEclipsePreferences[4];

		if (projectScope != null)
			nodes[0] = projectScope.getNode(languageName);
		else
			nodes[0] = null;
		
		nodes[1] = instanceScope.getNode(languageName);	
		nodes[2] = configurationScope.getNode(languageName);
		nodes[3] = defaultScope.getNode(languageName);
		
		return nodes;
	}
		

	
	
	public IScopeContext getScopeForLevel(String level)
	{
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException("PreferencesService.getScopeForLevel:  level = '" + level + "' is not a valid level");
		}
		//if (level.equals(PROJECT_LEVEL) && projectScope == null) {
		//	throw new IllegalStateException("PreferencesService.scopeForLevel:  scope for project requested when project scope is null");
		//}
			
		IScopeContext result = null;
		
		//if (!isaPreferencesLevel(level)) return result;

		if (level.equals(PROJECT_LEVEL)) {
			if (projectScope == null) { return null; }
			else { return projectScope; }
		} else if (level.equals(INSTANCE_LEVEL)) {
			return instanceScope;	
		} else if (level.equals(CONFIGURATION_LEVEL)) {
			return configurationScope;
		} else if (level.equals(DEFAULT_LEVEL)) {
			return defaultScope;
		}
		return result;
	}

	
	public IScopeContext getScopeForProject(IProject project) {
		if (project == null) {
			throw new IllegalArgumentException("PreferencesService.getScopeForProject:  given project is null");
		}
		return new ProjectScope(project);
	}
	
	
	public IEclipsePreferences[] getNodesForLevels(IProject project) {
		if (project == null) {
			throw new IllegalArgumentException("PreferencesService.getNodesForLevels:  given project is null");
		}
		
		IEclipsePreferences[] nodes = 	new IEclipsePreferences[4];
		
		nodes[0] = new ProjectScope(project).getNode(languageName);
		nodes[1] = instanceScope.getNode(languageName);
		nodes[2] = configurationScope.getNode(languageName);
		nodes[3] = defaultScope.getNode(languageName);
		
		return nodes;
	}
	
	
	public IEclipsePreferences getNodeForProject(IProject project) {
		if (project == null) {
			throw new IllegalArgumentException("PreferencesService.getNodeForProject:  given project is null");
		}
		
		return new ProjectScope(project).getNode(languageName);
	}
	
	
	public int getIndexForLevel(String level)
	{
		// Special case, e.g., for "applicable" preferences
		if (level == null) return 0;
		
		if (!isaPreferencesLevel(level)) {
			throw new IllegalArgumentException("PreferencesService.getIndexForLevel:  level = '" + level + "' is not a valid level");
		}
		// Maybe better let people get the index for the project level
		// even if there is not project assigned (index considered not harmful)
		//if (level.equals(PROJECT_LEVEL) && projectScope == null) {
		//	throw new IllegalStateException("PreferencesService.getIndexForLevel:  scope for project requested when project scope is null");
		//}

		if (level.equals(PROJECT_LEVEL))return 0;
		else if (level.equals(INSTANCE_LEVEL)) return 1;
		else if (level.equals(CONFIGURATION_LEVEL)) return 2;
		else if (level.equals(DEFAULT_LEVEL)) return 3;

		throw new IllegalStateException("PreferencesService.getIndexForLevel:  found no index to return for level = " + level);
	}
	
	
	/*
	 * For listeners
	 */
	
	
	private List projectSelectionListeners = new ArrayList();
	
	public void addProjectSelectionListener(IProjectSelectionListener listener) {
		projectSelectionListeners.add(listener);
	}
	
	public void removeProjectSelectionListener(IProjectSelectionListener listener) {
		projectSelectionListeners.remove(listener);
	}
	
	protected void fireProjectSelectionEvent(final ProjectSelectionEvent event)
	{
		if (projectSelectionListeners == null || projectSelectionListeners.size() == 0)
			return;
		//Object[] listeners = projectSelectionListeners.toArray();
		for (int i = 0; i < projectSelectionListeners.size(); i++) {
			final IProjectSelectionListener listener = (IProjectSelectionListener) projectSelectionListeners.get(i);
			ISafeRunnable job = new ISafeRunnable() {
				public void handleException(Throwable exception) {
					// already logged in Platform#run()
				}
				public void run() throws Exception {
					listener.selection(event);
				}
			};
			Platform.run(job);
		}
	}

	
}
