import groovy.json.JsonSlurperClassic


def call(sonarUrl, user, password) {
	ceTaskID = getCeTaskID('.sonar/report-task.txt')
	analysisId = getAnalysisId(ceTaskID, sonarUrl, user, password)
	checkQualityGateResult(analysisId, sonarUrl, user, password)
}



def getCeTaskID(resultFile) {
	def content = readFile resultFile
	Properties properties = new Properties()
	InputStream is = new ByteArrayInputStream(content.getBytes())
	properties.load(is)
	properties.ceTaskId
}


def checkQualityGateResult(analysisID, sonarUrl, user, password) {
	targetUrl = sonarUrl + "/api/qualitygates/project_status?analysisId=" + analysisID
	jsonObject = sh returnStdout: true, script: "curl -s -u $user:$password $targetUrl"
	def jsonSlurper = new JsonSlurperClassic()
	def object = jsonSlurper.parseText(jsonObject)
	if (object.projectStatus.status != "OK") {
		logQualityGateError(object.projectStatus)
		markUnstable("Quality Gate not passed")
	}
}


def logQualityGateError(jsonObject) {
	def conditions = jsonObject.conditions
	def headerPrinted = false
	for (int i = 0; i < conditions.size(); i++) {
		if (conditions[i].status != "OK") {
			if (!headerPrinted) {
				println ("\u001B[1mQuality Gates violated\u001B[0m")
				headerPrinted = true
			}
			if (conditions[i].status == "WARN") {
				println ("\u001B[33m" + conditions[i].metricKey + ": " + conditions[i].actualValue + " " + conditions[i].comparator + " " + conditions[i].errorThreshold + "\u001B[0m")
			}
			else if (conditions[i].status == "ERROR") {
				println ("\u001B[31m" + conditions[i].metricKey + ": " + conditions[i].actualValue + " " + conditions[i].comparator + " " + conditions[i].errorThreshold + "\u001B[0m")
			}
			else {
				println ("\u001B[31m" + conditions[i].status + " " + conditions[i].metricKey + ": " + conditions[i].actualValue + " " + conditions[i].comparator + " " + conditions[i].errorThreshold + "\u001B[0m")
			}
		}
	}
}

def markUnstable(errorMsg) {
	currentBuild.result = 'UNSTABLE'
	error errorMsg
}


@NonCPS
def parseJsonText(String jsonText) {
  final slurper = new JsonSlurperClassic()
  return new HashMap<>(slurper.parseText(jsonText))
}


def getAnalysisId(ceTaskID, sonarUrl, user, password) {
	targetUrl = sonarUrl + "/api/ce/task?id=" + ceTaskID
	jsonObject = sh returnStdout: true, script: "curl -s -u $user:$password $targetUrl"
	def object = parseJsonText(jsonObject)
	def retries = 0
	while (object.task.status.toString() == "IN_PROGRESS" && retries < 10) {
		retries++
		println "Analysis still in Progress - retrying $retries / 10" 
		sleep 30
		jsonObject = sh returnStdout: true, script: "curl -s -u $user:$password $targetUrl"
         	object = parseJsonText(jsonObject)
	}
	if (object.task.status.toString() != "SUCCESS") {
		markUnstable("$targetUrl not successful")
	}

	object.task.analysisId.toString()
}

