#!/bin/bash

project=$1

if [ -z "$project" ]
then
  project="generic"
fi


cd docker
docker compose down --remove-orphans --volumes
docker compose up -d
cd ..

sleep 30

echo ""
echo ""
echo "Add policy"
printf -v body -- '{
      "policy": {
        "phases": {
          "hot": {
            "min_age": "0ms",
            "actions": {
              "rollover": {
                "max_primary_shard_size": "50gb",
                "max_age": "2d"
              }
            }
          },
          "warm": {
            "min_age": "2d",
            "actions": {
              "set_priority": {
                "priority": 50
              }
            }
          },
          "cold": {
            "min_age": "4d",
            "actions": {
              "set_priority": {
                "priority": 0
              }
            }
          },
          "delete": {
            "min_age": "7d",
            "actions": {
              "delete": {}
            }
          }
        },
        "_meta": {
          "description": "Policy for %s"
        }
      }
    }
  ' ${project}
curl -k -X PUT "http://localhost:9200/_ilm/policy/logs-${project}" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d "${body}"

echo ""
echo ""
echo "Add _component_template settings"
printf -v body -- '{
      "template":{
        "settings": {
          "index.lifecycle.name": "logs-%s"
        }
      },
      "_meta": {
        "description": "Settings for %s"
      }
    }
  ' ${project} ${project}
curl -k -X PUT "http://localhost:9200/_component_template/logs-${project}-settings" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d "${body}"

echo ""
echo ""
echo "Add _component_template mappings"
printf -v body -- '{
      "template":{
        "settings": {
          "index.lifecycle.name": "logs-%s"
        }
      },
      "_meta": {
        "description": "Settings for %s"
      }
    }
  ' ${project} ${project}
curl -k -X PUT "http://localhost:9200/_component_template/logs-${project}-mappings" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d "${body}"

echo ""
echo ""
echo "Add _index_template"
printf -v body -- '{
      "index_patterns": [
        "logs-%s-*"
      ],
      "data_stream": {},
      "composed_of": [ "logs-%s-mappings", "logs-%s-settings" ],
      "priority": 500,
      "_meta": {
        "description": "Index template for %s"
      }
    }
  ' ${project} ${project} ${project} ${project}
curl -k -X PUT "http://localhost:9200/_index_template/logs-${project}" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d "${body}"

echo ""
echo ""
echo "Add space"
printf -v body -- '{
      "id": "%s",
      "name": "%s",
      "description" : "This is the %s Space",
      "color": "#4C54E7",
      "disabledFeatures": []
    }
  ' ${project} ${project} ${project}
curl -k -X POST "http://localhost:5601/api/spaces/space" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d "${body}"

echo ""
echo ""
echo "Add data_views"
printf -v body -- '{
      "data_view": {
         "title": "logs-%s-log-*",
         "name": "Log Data View",
         "timeFieldName": "@timestamp"
      }
    }
  ' ${project}
data_view=$(curl -k -X POST "http://localhost:5601/s/${project}/api/data_views/data_view" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d "${body}")

echo ""
echo ""
echo "Set default data_views"
printf -v body -- '{
    "data_view_id": "%s"
  }
  ' $(echo "$data_view" | jq ".data_view.id")
curl -X POST "http://localhost:5601/s/${project}/api/data_views/default"  \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d "${body}"

# Note: if the previous curl fails, you don't have the space set as default, not a big problem :)
