/*
* Check if ECS service exists for the specified cluster
*
* @return exit status
* */
def isECSServiceExists(cluster, service, region) {
    return sh(returnStatus: true, script: """
                        /usr/local/bin/aws ecs list-services \
                        --cluster ${cluster}  \
                        --region ${region} \
                        --output text | \
                        grep .*${service}
                    """)
}

/*
* Create a task definition
*
* @return exit status
* */
def registerTaskDefinition(taskFamily, taskName, memoryReservation, image, tag, containerPort, region) {
    return sh(returnStatus: true, script: """
                            /usr/local/bin/aws ecs register-task-definition --network-mode bridge \
                            --family ${taskFamily} \
                            --container-definitions '[{"name":"${taskName}", \
                                                    "image":"${image}", \
                                                     "memoryReservation": ${memoryReservation}, \
                                                     "logConfiguration": {"logDriver": "fluentd", "options": {"tag": "${tag}"}}, \
                                                    "portMappings":[{"containerPort":${containerPort}, "protocol":"tcp"}]}]' \
                            --region "${region}"
                        """)
}

/*
* Create target group
*
* @return exit status
* */
def createTargetGroup(name,
                      vpc,
                      port,
                      healthCheckPath,
                      healthCheckIntervalSec,
                      healthyThresholdCount,
                      unhealthyThresholdCount,
                      healthCheckSuccessCode,
                      region) {
    return sh(returnStdout: true, script: """
                                /usr/local/bin/aws elbv2 create-target-group \
                                --name ${name} \
                                --protocol HTTP \
                                --port ${port} \
                                --vpc-id ${vpc} \
                                --health-check-path ${healthCheckPath} \
                                --health-check-interval-seconds ${healthCheckIntervalSec} \
                                --healthy-threshold-count ${healthyThresholdCount} \
                                --unhealthy-threshold-count ${unhealthyThresholdCount} \
                                --matcher '{"HttpCode": "${healthCheckSuccessCode}"}' \
                                --query 'TargetGroups[0].TargetGroupArn' \
                                --output text \
                                --region ${region}
                            """).trim()
}

/*
* Modify target group parameters
*
* @return exit status
* */
def modifyTargetGroupAttr(tgArn, deRegDelay, region) {
    return sh(returnStatus: true, script: """
                                /usr/local/bin/aws elbv2 modify-target-group-attributes \
                                --target-group-arn ${tgArn} \
                                --attributes 'Key=deregistration_delay.timeout_seconds,Value=${deRegDelay}' \
                                --region ${region}
                            """)
}

/*
* Get priority for next LB listener rule
*
* @return exit status
* */
def getNextALBRulePriority(listenerArn, region) {
    return sh(returnStdout: true, script: """
                            /usr/local/bin/aws elbv2 describe-rules \
                            --listener-arn ${listenerArn} \
                            --region ${region} | \
                            jq -r '[.Rules[].Priority][0:-1] | map(.|tonumber) | max + 1'
                        """).trim()
}

/*
* Create ALB listener rule
*
* @return exit status
* */
def createALBListenerRule(listenerArn, priority, host, targetGroupArn, region) {
    return sh(returnStatus: true, script: """
                                    /usr/local/bin/aws elbv2 create-rule \
                                    --listener-arn ${listenerArn} \
                                    --priority ${priority} \
                                    --conditions Field=host-header,Values='"${host}"' \
                                    --actions Type=forward,TargetGroupArn=${targetGroupArn} \
                                    --region ${region}
                                """)
}

/*
* Create ECS service
*
* @return exit status
* */
def createECSService(name, cluster, taskFamily, desiredCount, tgArn, taskName, containerPort, region) {
    sh(returnStatus: true, script: """
                                /usr/local/bin/aws ecs create-service \
                                --service-name ${name} \
                                --launch-type EC2 \
                                --cluster ${cluster} \
                                --task-definition ${taskFamily} \
                                --desired-count ${desiredCount} \
                                --load-balancers '[{"targetGroupArn":"${tgArn}", \
                                                "containerName":"${taskName}", \
                                                "containerPort":${containerPort}}]' \
                                --role ecsServiceRole \
                                --region ${region}
                            """)
}

/*
* Fetch ALB listern ARN
* */
def getListenerRuleArn(listenerArn, host, region) {
    return sh(returnStdout: true, script: """
                                    /usr/local/bin/aws elbv2 describe-rules \
                                    --listener-arn  ${listenerArn} \
                                    --region ${region} | \
                                    jq -r '[.Rules[]][] | select(.Conditions[].Values[]=="${host}") | .RuleArn'
                                """).trim()
}

/*
* Delete ALB listener rule
* */
def deleteListenerRule(ruleArn, region) {
    return sh(returnStatus: true, script: """
                                    /usr/local/bin/aws elbv2 delete-rule \
                                    --rule-arn ${ruleArn} \
                                    --region ${region}
                                """)
}

/*
* Get Target group ARN
* */
def getTargetGroupArn(listenerArn, host, region) {
    return sh(returnStdout: true, script: """
                                    /usr/local/bin/aws elbv2 describe-rules \
                                    --listener-arn  ${listenerArn} \
                                    --region ${region} | \
                                    jq -r '[.Rules[]][] | select(.Conditions[].Values[]=="${host}") | .Actions[0].TargetGroupArn'
                                """).trim()
}

/*
* Delete Target group
* */
def deleteTargetGroup(tgArn, region) {
    return sh(returnStatus: true, script: """
                                    /usr/local/bin/aws elbv2 delete-target-group \
                                    --target-group-arn ${tgArn} \
                                    --region ${region}
                                """)
}

/*
* Delete ECS service
* */
def deleteECSService(name, cluster, region) {
    sh(returnStatus: true, script: """
                                /usr/local/bin/aws ecs delete-service \
                                --service ${name} \
                                --cluster ${cluster} \
                                --force \
                                --region ${region}
                            """)
}

/*
* Deregister Task Definaton 
* */
def deregisterTaskDefinition(taskName, tag, region) {
    return sh(returnStatus: true, script: """
                           /usr/local/bin/aws ecs deregister-task-definition \
			   --task-definition ${taskName}:${tag} \
                           --region ${region}
                       """)
}