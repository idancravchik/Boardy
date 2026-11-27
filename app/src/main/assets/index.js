
// Register blocks from JSON
Blockly.common.defineBlocksWithJsonArray(blocks);

// זה ההגדרות של הסביבת בלוקלי ואיך זזים בה
const blocklyDiv = document.getElementById('blocklyDiv');
const ws = Blockly.inject(blocklyDiv, { 
    toolbox,
    rtl: true,
    renderer: 'zelos',
    horizontalLayout: true,
    toolboxPosition: 'end',
    trashcan: true,
    grid: {
            spacing: 20,
            length: 3,
            colour: '#ccc',
            snap: true
          },
    trashcan: true,
    move: {
      scrollbars: {
        horizontal: true,
        vertical: true
      },
      drag: true,
      wheel: false
    }
 });

window.addEventListener('resize', () => Blockly.svgResize(ws));
