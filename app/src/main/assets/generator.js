const cGenerator = new Blockly.Generator('C');
const Order = {
  ATOMIC: 0,
  MEMBER: 1,
  FUNCTION_CALL: 2,
  INCREMENT: 3,
  DECREMENT: 3,
  BITWISE_NOT: 4,
  UNARY_PLUS: 4,
  UNARY_NEGATION: 4,
  LOGICAL_NOT: 4,
  MULTIPLICATION: 5,
  DIVISION: 5,
  MODULUS: 5,
  ADDITION: 6,
  SUBTRACTION: 6,
  RELATIONAL: 8,
  EQUALITY: 9,
  LOGICAL_AND: 13,
  LOGICAL_OR: 14,
  ASSIGNMENT: 16,
  NONE: 99
};
cGenerator.init = function(workspace) { // איפוס ויצירת משתנים
  this.definitions_ = Object.create(null);
  this.setups_ = Object.create(null);
};

cGenerator.scrub_ = function (block, code, opt_thisOnly) { //ברירת מחדל
  const nextBlock = block.nextConnection && block.nextConnection.targetBlock();
  if (nextBlock && !opt_thisOnly) {
    return code + '\n' + cGenerator.blockToCode(nextBlock);
  }
  return code;
};

cGenerator.finish = function (code) { // אוסף הכל לקוד סופי
  const definitions = Object.values(this.definitions_).join('\n');
  const setups = Object.values(this.setups_).join('\n  ');

  return `${definitions}

void setup() {
  ${setups}
}

void loop() {
  ${code}
}
`;
};

cGenerator.workspaceToCode = function (workspace) {
  this.init(workspace);
  const startBlock = workspace.getBlocksByType('start', false)[0];
  const code = startBlock ? this.blockToCode(startBlock) : '';
  return this.finish(code.trim());
};

// -------------------------------------------------------------

cGenerator.forBlock['start'] = function () {
  return '';
};

cGenerator.forBlock['text'] = function (block) {
  const text = block.getFieldValue('NAME');
  return [JSON.stringify(text), Order.ATOMIC];
}

cGenerator.forBlock['connect'] = function (block) {
  const part1 = cGenerator.valueToCode(block, 'VALUE1');
  const part2 = cGenerator.valueToCode(block, 'VALUE2');

const connectfunc = cGenerator.provideFunction_('connectfunc', 
  `
    #include <string.h>

    static char __str_buf[64];

    char* connectfunc(const char* s1, const char* s2) {
      __str_buf[0] = '\\0';
      strncat(__str_buf, s1, sizeof(__str_buf) - 1);
      strncat(__str_buf, s2, sizeof(__str_buf) - strlen(__str_buf) - 1);
      return __str_buf;
    }
    `
);

const code = `${connectfunc}(${part1}, ${part2})`;
  return [code, Order.ATOMIC];
} // לא מוכן

cGenerator.forBlock['compare'] = function (block) {
  const OPERATORS = {
    'greater': '>',
    'lesser': '<',
    'lesserequal': '<=',
    'graterequal': '>=',
    'equal': '=='
  };

  const opKey = block.getFieldValue('operation');
  const op = OPERATORS[opKey] || '==';

  const order = (op === '==') 
    ? Order.EQUALITY 
    : Order.RELATIONAL;

  const leftValue = cGenerator.valueToCode(block, 'compare', order) || '0';
  const rightNumber = block.getFieldValue('number');

  const code = `${leftValue} ${op} ${rightNumber}`;
  return [code, order];

}

cGenerator.forBlock['or'] = function (block) {
  const part1 = cGenerator.valueToCode(block, 'VALUE1');
  const part2 = cGenerator.valueToCode(block, 'VALUE2');
} // לא מוכן

cGenerator.forBlock['and'] = function (block) {
  const part1 = cGenerator.valueToCode(block, 'VALUE1');
  const part2 = cGenerator.valueToCode(block, 'VALUE2');
} // לא מוכן

cGenerator.forBlock['delay'] = function (block) {
  const delay = block.getFieldValue('delay') * 1000;
  return `delay(${delay});`
}

cGenerator.forBlock['if_then'] = function (block) {
  const condition = cGenerator.valueToCode(block, 'CONDITION', Order.ATOMIC) || false;
  const statement = cGenerator.statementToCode(block, 'STATEMENT', Order.ATOMIC) || '';

  return `if (${condition}) {\n${statement}\n}\n`;

}

cGenerator.forBlock['if_then_else'] = function (block) {
  const condition = cGenerator.valueToCode(block, 'CONDITION', Order.ATOMIC) || false;
  const statement = cGenerator.statementToCode(block, 'STATEMENT', Order.ATOMIC) || '';
  const else_statement = cGenerator.statementToCode(block, 'ELSE', Order.ATOMIC) || '';

  return `if (${condition}) {\n${statement}\n} else {\n${else_statement}\n}\n`;

}

cGenerator.forBlock['for_loop'] = function (block) {
  const repetitions = block.getFieldValue('ITERATE');
  const statement = cGenerator.statementToCode(block, 'STATMENT');

  return `for (int i = 0; i < ${repetitions}; i++) {\n${statement}\n}\n`;
}

cGenerator.forBlock['while_loop'] = function (block) {
  const condition = cGenerator.valueToCode(block, 'CONDITION', Order.ATOMIC) || 'false';
  const statement = cGenerator.statementToCode(block, 'STATMENT');

  return `while (${condition}) {\n${statement}\n}\n`;
}

cGenerator.forBlock['print'] = function (block) {
  const text = cGenerator.valueToCode(block, 'PRINT', Order.ATOMIC) || '';

  return `USBSerial_println(${text});`;
}













cGenerator.forBlock['init_servo'] = function (block) {
  const pin = block.getFieldValue('PIN');

  cGenerator.definitions_['include_servo'] = '#include <Servo.h>';
  cGenerator.definitions_['var_servo'] = 'Servo myServo;';

  cGenerator.setups_['setup_servo'] = `myServo.attach(${pin});`;

  return `myServo.write(90);`;
};