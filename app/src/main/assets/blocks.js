const blocks = [
{
  "type": "delay",
  "tooltip": "מחכה כמה שניות",
  "helpUrl": "",
  "message0": "חכה %1 שניות %2",
  "args0": [
    {
      "type": "field_number",
      "name": "delay",
      "value": 0
    },
    {
      "type": "input_dummy",
      "name": "delay"
    }
  ],
  "previousStatement": null,
  "nextStatement": null,
  "colour": "#F1BF42"
},
{
  "type": "compare",
  "tooltip": "",
  "helpUrl": "",
  "message0": "%1 %2 %3 %4",
  "args0": [
    {
      "type": "input_value",
      "name": "compare",
      "align": "RIGHT",
      "check": "Number"
    },
    {
      "type": "field_dropdown",
      "name": "operation",
      "options": [
        [
          "<",
          "greater"
        ],
        [
          ">",
          "lesser"
        ],
        [
          "≥",
          "lesserequal"
        ],
        [
          "≤",
          "graterequal"
        ],
        [
          "=",
          "equal"
        ]
      ]
    },
    {
      "type": "field_number",
      "name": "number",
      "value": 0
    },
    {
      "type": "input_dummy",
      "name": "compareto"
    }
  ],
  "output": "Boolean",
  "colour": "#F1BF42"
},
{
  "type": "text",
  "tooltip": "",
  "helpUrl": "",
  "message0": "טקסט %1 %2",
  "args0": [
    {
      "type": "field_input",
      "name": "NAME",
      "text": "מילה"
    },
    {
      "type": "input_dummy",
      "name": "text"
    }
  ],
  "output": "String",
  "colour": "#F1BF42"
},
{
  "type": "if_then",
  "tooltip": "",
  "helpUrl": "",
  "message0": "אם %1 אז %2",
  "args0": [
    {
      "type": "input_value",
      "name": "CONDITION",
      "check": "Boolean"
    },
    {
      "type": "input_statement",
      "name": "STATEMENT"
    }
  ],
  "previousStatement": null,
  "nextStatement": null,
  "colour": "#5383EC"
},
{
  "type": "if_then_else",
  "tooltip": "",
  "helpUrl": "",
  "message0": "אם %1 אז %2 ואם לא אז %3",
  "args0": [
    {
      "type": "input_value",
      "name": "CONDITION",
      "check": "Boolean"
    },
    {
      "type": "input_statement",
      "name": "STATEMENT"
    },
    {
      "type": "input_statement",
      "name": "ELSE"
    }
  ],
  "previousStatement": null,
  "nextStatement": null,
  "colour": "#5383EC"
},
{
  "type": "for_loop",
  "tooltip": "",
  "helpUrl": "",
  "message0": "תחזור על זה %1 פעמים %2 %3",
  "args0": [
    {
      "type": "field_number",
      "name": "ITERATE",
      "value": 0
    },
    {
      "type": "input_dummy",
      "name": "ITERATE"
    },
    {
      "type": "input_statement",
      "name": "STATMENT"
    }
  ],
  "previousStatement": null,
  "nextStatement": null,
  "colour": "#5383EC"
},
{
  "type": "while_loop",
  "tooltip": "",
  "helpUrl": "",
  "message0": "כל עוד %1 %2",
  "args0": [
    {
      "type": "input_value",
      "name": "CONDITION",
      "check": "Boolean"
    },
    {
      "type": "input_statement",
      "name": "STATMENT"
    }
  ],
  "previousStatement": null,
  "nextStatement": null,
  "colour": "#5383EC"
},
{
  "type": "or",
  "tooltip": "",
  "helpUrl": "",
  "message0": "%1 או ש %2",
  "args0": [
    {
      "type": "input_value",
      "name": "STATMENT1",
      "check": "Boolean"
    },
    {
      "type": "input_value",
      "name": "STATMENT2",
      "check": "Boolean"
    }
  ],
  "output": "Boolean",
  "colour": "#F1BF42",
  "inputsInline": true
},
{
  "type": "and",
  "tooltip": "",
  "helpUrl": "",
  "message0": "%1 וגם %2",
  "args0": [
    {
      "type": "input_value",
      "name": "STATMENT1",
      "check": "Boolean"
    },
    {
      "type": "input_value",
      "name": "STATMENT2",
      "check": "Boolean"
    }
  ],
  "output": "Boolean",
  "colour": "#F1BF42",
  "inputsInline": true
}
,
{
  "type": "print",
  "tooltip": "",
  "helpUrl": "",
  "message0": "הדפס %1",
  "args0": [
    {
      "type": "input_value",
      "name": "PRINT",
      "align": "RIGHT",
      "check": "String"
    }
  ],
  "previousStatement": null,
  "nextStatement": null,
  "colour": "#58A65C",
  "inputsInline": true
},
{
  "type": "connect",
  "tooltip": "",
  "helpUrl": "",
  "message0": "%1 %2",
  "args0": [
    {
      "type": "input_value",
      "name": "VALUE1",
      "align": "RIGHT",
      "check": "String"
    },
    {
      "type": "input_value",
      "name": "VALUE2",
      "align": "RIGHT",
      "check": "String"
    }
  ],
  "output": "String",
  "colour": "#F1BF42",
  "inputsInline": true
},
{
  "type": "read",
  "tooltip": "",
  "helpUrl": "",
  "message0": "מידע מהטלפון %1",
  "args0": [
    {
      "type": "input_dummy",
      "name": "NAME"
    }
  ],
  "output": "String",
  "colour": "#58A65C",
  "inputsInline": true
},
{
  "type": "available",
  "tooltip": "",
  "helpUrl": "",
  "message0": "התקבל מידע מהטלפון %1",
  "args0": [
    {
      "type": "input_dummy",
      "name": "available"
    }
  ],
  "output": "Boolean",
  "colour": "#58A65C",
  "inputsInline": true
},
{
  "type": "led_on",
  "tooltip": "",
  "helpUrl": "",
  "message0": "הדלק את הנורה בצבע  %1 %2",
  "args0": [
    {
      "type": "field_dropdown",
      "name": "COLOR",
      "options": [
        [
          "🔴",
          "RED"
        ],
        [
          "🟢",
          "GREEN"
        ]
      ]
    },
    {
      "type": "input_dummy",
      "name": "COLOR"
    }
  ],
  "previousStatement": null,
  "nextStatement": null,
  "colour": "#14B8A6",
  "inputsInline": true
},
{
  "type": "led_off",
  "tooltip": "",
  "helpUrl": "",
  "message0": "כבה את המנורה %1",
  "args0": [
    {
      "type": "input_dummy",
      "name": "OFF"
    }
  ],
  "previousStatement": null,
  "nextStatement": null,
  "colour": "#14B8A6",
  "inputsInline": true
},
{
  "type": "temp",
  "tooltip": "",
  "helpUrl": "",
  "message0": "הטמפרטורה הנוכחית %1",
  "args0": [
    {
      "type": "input_dummy",
      "name": "OFF"
    }
  ],
  "output": "Number",
  "colour": "#7C3AED",
  "inputsInline": true
},
{
  "type": "hum",
  "tooltip": "",
  "helpUrl": "",
  "message0": "הלחות הנוכחית %1",
  "args0": [
    {
      "type": "input_dummy",
      "name": "OFF"
    }
  ],
  "output": "Number",
  "colour": "#7C3AED",
  "inputsInline": true
},
{
  "type": "bt_state",
  "tooltip": "",
  "helpUrl": "",
  "message0": "הכפתור %1 %2",
  "args0": [
    {
      "type": "field_dropdown",
      "name": "STATE",
      "options": [
        [
          "לחוץ",
          "pressed"
        ],
        [
          "לא לחוץ",
          "not_pressed"
        ]
      ]
    },
    {
      "type": "input_dummy",
      "name": "STATE"
    }
  ],
  "output": "Boolean",
  "colour": "#7C3AED",
  "inputsInline": true
}
]
