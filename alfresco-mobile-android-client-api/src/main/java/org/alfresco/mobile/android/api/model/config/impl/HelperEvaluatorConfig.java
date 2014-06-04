package org.alfresco.mobile.android.api.model.config.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.alfresco.mobile.android.api.constants.ConfigConstants;
import org.alfresco.mobile.android.api.constants.OnPremiseConstant;
import org.alfresco.mobile.android.api.model.RepositoryInfo;
import org.alfresco.mobile.android.api.model.config.ConfigInfo;
import org.alfresco.mobile.android.api.model.config.EvaluatorType;
import org.alfresco.mobile.android.api.model.config.OperatorType;
import org.alfresco.mobile.android.api.model.impl.RepositoryVersionHelper;
import org.apache.chemistry.opencmis.commons.impl.JSONConverter;

import android.os.Bundle;
import android.util.Log;

public class HelperEvaluatorConfig extends HelperConfig
{
    private static final String TAG = HelperEvaluatorConfig.class.getSimpleName();

    private LinkedHashMap<String, EvaluatorConfigData> evaluatorIndex;

    // ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    // ///////////////////////////////////////////////////////////////////////////
    public HelperEvaluatorConfig(ConfigurationImpl context, HelperStringConfig localHelper)
    {
        super(context, localHelper);
    }

    public void addEvaluators(Map<String, Object> json)
    {
        evaluatorIndex = new LinkedHashMap<String, EvaluatorConfigData>(json.size());
        EvaluatorConfigData evalConfig = null;
        for (Entry<String, Object> entry : json.entrySet())
        {
            evalConfig = EvaluatorConfigData.parse(entry.getKey(), JSONConverter.getMap(entry.getValue()));
            if (evalConfig == null)
            {
                continue;
            }
            evaluatorIndex.put(evalConfig.identifier, evalConfig);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    // ///////////////////////////////////////////////////////////////////////////
    public boolean evaluateIfEvaluator(Map<String, Object> evaluatorsConfiguration, Bundle extraParameters)
    {
        if (!evaluatorsConfiguration.containsKey(ConfigConstants.EVALUATOR)) { return true; }
        return evaluate(JSONConverter.getString(evaluatorsConfiguration, ConfigConstants.EVALUATOR), extraParameters);
    }

    public boolean evaluate(String evaluatorId, Bundle extraParameters)
    {
        if (evaluatorId == null) { return true; }
        return resolveEvaluator(evaluatorId, extraParameters);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // EVALUATION
    // ///////////////////////////////////////////////////////////////////////////
    protected boolean resolveEvaluator(String evaluatorId, Bundle extraParameters)
    {
        Boolean result = false;

        if (!evaluatorIndex.containsKey(evaluatorId))
        {
            Log.w(TAG, "Evaluator  [" + evaluatorId + "] doesn't exist. Check your configuration.");
            return false;
        }

        EvaluatorConfigData evalConfig = evaluatorIndex.get(evaluatorId);
        if (evalConfig == null) { return false; }
        if (evalConfig.hasMatchOperator)
        {
            boolean intermediateResult = false;
            for (String evalId : evalConfig.evaluatorIds)
            {
                intermediateResult = resolveEvaluator(evalId, extraParameters);
                // If result == True && match any we can exit
                if (intermediateResult && ConfigConstants.MATCH_ANY_VALUE.equals(evalConfig.matchOperator))
                {
                    result = true;
                    break;
                }
                // If result == false && match all we can exit
                else if (!intermediateResult && ConfigConstants.MATCH_ALL_VALUE.equals(evalConfig.matchOperator))
                {
                    result = false;
                    break;
                }
                // else we continue
            }
        }
        else
        {
            result = resolveEvaluator(evalConfig, extraParameters);
        }

        return result;
    }

    protected boolean resolveEvaluator(EvaluatorConfigData evalConfig, Bundle extraParameters)
    {
        Boolean result = false;

        EvaluatorType configtype = EvaluatorType.fromValue(evalConfig.type);
        if (configtype == null)
        {
            Log.w(TAG, "Evaluator Type  [" + evalConfig.type + "]  for [" + evalConfig.identifier
                    + "] doesn't exist. Check your configuration.");
            return false;
        }

        switch (configtype)
        {
            case IS_REPOSITORY_VERSION:
                result = evaluateRepositoryVersion(evalConfig);
                break;
            default:
                break;
        }

        Log.d(TAG, "EVALUATOR [" + evalConfig.identifier + "] : " + ((evalConfig.hasNegation) ? !result : result));

        return (evalConfig.hasNegation) ? !result : result;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // EVALUATION REGISTRY
    // ///////////////////////////////////////////////////////////////////////////
    private boolean evaluateRepositoryVersion(EvaluatorConfigData evalConfig)
    {
        boolean result = true;

        if (!hasConfiguration() || ((ConfigurationImpl) getConfiguration()).getSession() == null)
        {
            Log.w(TAG, "Evaluator  [" + evalConfig.identifier + "] requires a session.");
            return false;
        }

        RepositoryInfo repoInfo = ((ConfigurationImpl) getConfiguration()).getSession().getRepositoryInfo();

        // Edition
        if (evalConfig.configPropertiesMap.containsKey(ConfigConstants.EDITION_VALUE))
        {
            result = repoInfo.getEdition().equalsIgnoreCase(
                    JSONConverter.getString(evalConfig.configPropertiesMap, ConfigConstants.EDITION_VALUE));
        }

        if (!result) { return false; }

        OperatorType operator = OperatorType.EQUAL;
        if (evalConfig.configPropertiesMap.containsKey(ConfigConstants.OPERATOR_VALUE))
        {
            operator = OperatorType.fromValue(JSONConverter.getString(evalConfig.configPropertiesMap,
                    ConfigConstants.OPERATOR_VALUE));
        }

        if (operator == null)
        {
            Log.w(TAG, "Evaluator  [" + evalConfig.identifier + "] has a wrong operator [" + evalConfig.type
                    + "]. Check your configuration.");
            return false;
        }

        int versionNumber = 0;
        int repoVersionNumber = 0;
        // Major Version
        if (evalConfig.configPropertiesMap.containsKey(ConfigConstants.MAJORVERSION_VALUE))
        {
            versionNumber += 100 * JSONConverter.getInteger(evalConfig.configPropertiesMap,
                    ConfigConstants.MAJORVERSION_VALUE).intValue();
            repoVersionNumber += 100 * repoInfo.getMajorVersion();
        }

        // Minor Version
        if (evalConfig.configPropertiesMap.containsKey(ConfigConstants.MINORVERSION_VALUE))
        {
            versionNumber += 10 * JSONConverter.getInteger(evalConfig.configPropertiesMap,
                    ConfigConstants.MINORVERSION_VALUE).intValue();
            repoVersionNumber += 10 * repoInfo.getMinorVersion();
        }

        // Maintenance Version
        if (evalConfig.configPropertiesMap.containsKey(ConfigConstants.MAINTENANCEVERSION_VALUE))
        {
            if (repoInfo.getEdition().equals(OnPremiseConstant.ALFRESCO_EDITION_ENTERPRISE))
            {
                versionNumber += JSONConverter.getInteger(evalConfig.configPropertiesMap,
                        ConfigConstants.MAINTENANCEVERSION_VALUE).intValue();
                repoVersionNumber += repoInfo.getMaintenanceVersion();
            }
            else
            {
                result = evaluate(operator, RepositoryVersionHelper.getVersionString(repoInfo.getVersion(), 2),
                        JSONConverter.getString(evalConfig.configPropertiesMap,
                                ConfigConstants.MAINTENANCEVERSION_VALUE));
            }
        }

        result = evaluate(operator, repoVersionNumber, versionNumber);

        return result;
    }

    private boolean evaluate(OperatorType operator, int value, int valueExpected)
    {
        switch (operator)
        {
            case INFERIOR:
                return value < valueExpected;
            case INFERIOR_OR_EQUAL:
                return value <= valueExpected;
            case SUPERIOR_OR_EQUAL:
                return value >= valueExpected;
            case SUPERIOR:
                return value > valueExpected;
            default:
                return value == valueExpected;
        }
    }

    private boolean evaluate(OperatorType operator, String value, String valueExpected)
    {
        int compareValue = value.compareTo(valueExpected);
        switch (operator)
        {
            case INFERIOR:
                return compareValue < 0;
            case INFERIOR_OR_EQUAL:
                return compareValue <= 0;
            case SUPERIOR_OR_EQUAL:
                return compareValue >= 0;
            case SUPERIOR:
                return compareValue > 0;
            default:
                return compareValue == 0;
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // INTERNAL UTILITY CLASS
    // ///////////////////////////////////////////////////////////////////////////
    protected static class EvaluatorConfigData extends ConfigImpl
    {
        public String identifier;

        public boolean hasNegation = false;

        public boolean hasMatchOperator = false;

        public String type;

        public String matchOperator;

        public ArrayList<String> evaluatorIds;

        // ///////////////////////////////////////////////////////////////////////////
        // CONSTRUCTORS
        // ///////////////////////////////////////////////////////////////////////////
        EvaluatorConfigData()
        {
            super();
        }

        static EvaluatorConfigData parse(String identifier, Map<String, Object> json)
        {
            EvaluatorConfigData eval = new EvaluatorConfigData();
            if (identifier.startsWith(ConfigConstants.NEGATE_SYMBOL))
            {
                eval.identifier = identifier.replace(ConfigConstants.NEGATE_SYMBOL, "");
                eval.hasNegation = true;
            }
            else
            {
                eval.identifier = identifier;
            }

            eval.type = JSONConverter.getString(json, ConfigConstants.TYPE_VALUE);

            if (json.containsKey(ConfigConstants.MATCH_ALL_VALUE))
            {
                eval.matchOperator = ConfigConstants.MATCH_ALL_VALUE;
                eval.hasMatchOperator = true;
            }
            else if (json.containsKey(ConfigConstants.MATCH_ANY_VALUE))
            {
                eval.matchOperator = ConfigConstants.MATCH_ANY_VALUE;
                eval.hasMatchOperator = true;
            }
            else
            {
                eval.configPropertiesMap = JSONConverter.getMap(json.get(ConfigConstants.PARAMS_VALUE));
            }

            if (eval.matchOperator != null)
            {
                List<Object> idsObject = JSONConverter.getList(json.get(eval.matchOperator));
                ArrayList<String> ids = new ArrayList<String>(idsObject.size());
                for (Object object : idsObject)
                {
                    ids.add((String) object);
                }
                eval.evaluatorIds = ids;
            }

            return eval;
        }
    }
}
