#!/bin/bash

cd docker-infra
docker compose down --remove-orphans --volumes
docker compose up -d
cd ..
exit 0

sleep 20

echo ""
echo ""
echo "Add policy"
curl -k -X PUT "http://localhost:9200/_ilm/policy/logs-fdr" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
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
            "description": "Policy for FDR"
          }
        }
      }
    '

echo ""
echo ""
echo "Add _component_template settings"
curl -k -X PUT "http://localhost:9200/_component_template/logs-fdr-settings" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
        "template":{
          "settings": {
            "index.lifecycle.name": "logs-fdr"
          }
        },
        "_meta": {
          "description": "Settings for FDR"
        }
      }
    '

echo ""
echo ""
echo "Add _component_template mappings"
curl -k -X PUT "http://localhost:9200/_component_template/logs-fdr-mappings" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
        "template":{
          "settings": {
            "index.lifecycle.name": "logs-fdr"
          }
        },
        "_meta": {
          "description": "Settings for FDR"
        }
      }
    '

echo ""
echo ""
echo "Add _index_template"
curl -k -X PUT "http://localhost:9200/_index_template/logs-fdr" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
        "index_patterns": [
          "logs-fdr-*"
        ],
        "data_stream": {},
        "composed_of": [ "logs-fdr-mappings", "logs-fdr-settings" ],
        "priority": 500,
        "_meta": {
          "description": "Index template for FDR"
        }
      }
    '

echo ""
echo ""
echo "Add space"
curl -k -X POST "http://localhost:5601/api/spaces/space" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
        "id": "fdr",
        "name": "Flussi di rendicontazione",
        "description" : "This is the FDR Space",
        "color": "#4C54E7",
        "disabledFeatures": []
      }
    '

echo ""
echo ""
echo "Add data_views"
data_view=$(curl -k -X POST "http://localhost:5601/s/fdr/api/data_views/data_view" \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d '{
        "data_view": {
           "title": "logs-fdr-log-*",
           "name": "FDR log Data View",
           "timeFieldName": "@timestamp"
        }
      }
    ')

echo ""
echo ""
echo "Set default data_views"
data_view_id=$(echo $data_view | jq ".data_view.id")
curl -X POST "http://localhost:5601/s/fdr/api/data_views/default"  \
  -H 'kbn-xsrf: true' \
  -H 'Content-Type: application/json' \
  -d'{
      "data_view_id": "${data_view_id}"
    }
    '
