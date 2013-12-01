package net.minecraftforge.gradle;

import java.util.List;

import net.minecraftforge.gradle.common.Constants;
import argo.jdom.JsonField;
import argo.jdom.JsonNode;

public class JsonUtil {

    public static boolean ruleMatches(List<JsonNode> rules)
    {
        boolean testPositive = false;
        boolean result = false;
        for (JsonNode node : rules)
        {
            if (node.getFieldList().size() == 1) // single statement
            {
                continue;
            }
            if ("allow".equals(node.getStringValue("action")))
            {
                testPositive = true;
            }
            else if ("disallow".equals(node.getStringValue("action")))
            {
                testPositive = false;
            }

            for (JsonField test : node.getFieldList())
            {
                if ("action".equals(test.getName().getText()))
                {
                    continue;
                }
                boolean testResult = assertTest(test);
                result |= (testPositive ?  testResult: !testResult);
            }
        }
        return result;
    }

    private static boolean assertTest(JsonField test)
    {
        if ("os".equals(test.getName().getText()))
        {
            return Constants.OPERATING_SYSTEM.toString().equals(test.getValue().getStringValue("name"));
        }
        return false;
    }

}
