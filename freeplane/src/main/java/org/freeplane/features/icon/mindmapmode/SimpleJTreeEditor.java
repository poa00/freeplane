package org.freeplane.features.icon.mindmapmode;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.StringTokenizer;

import javax.swing.AbstractAction;
import javax.swing.AbstractCellEditor;
import javax.swing.ActionMap;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.freeplane.core.ui.components.TagIcon;
import org.freeplane.core.util.ColorUtils;
import org.freeplane.features.icon.IconRegistry;
import org.freeplane.features.icon.Tag;

public class SimpleJTreeEditor extends JFrame {
    private JTree tree;
    private DefaultTreeModel model;
    private IconRegistry registry = new IconRegistry();

    public SimpleJTreeEditor() {
        super("JTree Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 500);
        setLocationRelativeTo(null);

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Tags");
        model = new DefaultTreeModel(rootNode);
        tree = new JTree(model) {

            @Override
            public boolean isPathEditable(TreePath path) {
                Object lastPathComponent = path.getLastPathComponent();
                if(! (lastPathComponent instanceof DefaultMutableTreeNode))
                    return false;
                Object userObject = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();
                return userObject instanceof Tag;
             }

        };
        tree.setEditable(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setInvokesStopCellEditing(true);
        tree.setDragEnabled(true);
        tree.setDropMode(DropMode.ON_OR_INSERT);
        tree.setTransferHandler(new TreeTransferHandler());
        tree.setCellRenderer(new CustomLeafRenderer(rootNode));
        tree.setCellEditor(new MyNodeCellEditor(registry));

        JScrollPane scrollPane = new JScrollPane(tree);
        getContentPane().add(scrollPane, BorderLayout.CENTER);

        configureKeyBindings();
    }

    private void configureKeyBindings() {
        InputMap im = tree.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = tree.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK), "addNode");
        am.put("addNode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addNode();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK), "removeNode");
        am.put("removeNode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                removeNode();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), "copyNode");
        am.put("copyNode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyNode();
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK), "pasteNode");
        am.put("pasteNode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pasteNode();
            }
        });
    }

    private void addNode() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            selectedNode = (DefaultMutableTreeNode) model.getRoot();
        }
        DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(registry.createTag("New Node"));
        model.insertNodeInto(newNode, selectedNode, selectedNode.getChildCount());
        TreeNode[] nodes = model.getPathToRoot(newNode);
        TreePath path = new TreePath(nodes);
        tree.scrollPathToVisible(path);
        tree.setSelectionPath(path);
        tree.startEditingAtPath(path);
    }

    private void removeNode() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selectedNode != null && selectedNode.getParent() != null) {
            model.removeNodeFromParent(selectedNode);
        }
    }


    private void copyNode() {
        TreePath currentPath = tree.getSelectionPath();
        if (currentPath != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) currentPath.getLastPathComponent();
            String serializedData = serializeTreeNode(node, "", true);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection stringSelection = new StringSelection(serializedData);
            clipboard.setContents(stringSelection, null);
        }
    }

    private void pasteNode() {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String data = (String) contents.getTransferData(DataFlavor.stringFlavor);
                DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (selectedNode == null) {
                    selectedNode = (DefaultMutableTreeNode) model.getRoot();
                }
                deserializeTreeNode(data, selectedNode, true);
                ((DefaultTreeModel) tree.getModel()).nodeStructureChanged(selectedNode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String serializeTreeNode(DefaultMutableTreeNode node, String indent, boolean withColor) {
        int childCount = node.getChildCount();
        Object userObject = node.getUserObject();
        StringBuilder builder;
        if(node != model.getRoot()) {
            Tag tag = (Tag) userObject;
            builder = new StringBuilder(indent + tag.getContent());
            if(withColor && childCount == 0)
                builder.append(ColorUtils.colorToRGBAString(tag.getIconColor()));
            builder.append("\n");
            indent = indent + " ";
        }
        else
            builder = new StringBuilder();
        for (int i = 0; i < childCount; i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) node.getChildAt(i);
            builder.append(serializeTreeNode(childNode, indent, withColor));
        }
        return builder.toString();
    }

    private void deserializeTreeNode(String data, DefaultMutableTreeNode parentNode, boolean withColor) {
        StringTokenizer st = new StringTokenizer(data, "\n");
        DefaultMutableTreeNode lastNode = parentNode;
        int lastLevel = -1;
        int currentLevel = 0;
        while (st.hasMoreTokens()) {
            String line = st.nextToken();
            currentLevel = 0;
            while (currentLevel < line.length() && line.charAt(currentLevel) == ' ') {
                currentLevel++;
            }
            String nodeName = line.trim();
            DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(nodeName);
            if (currentLevel > lastLevel) {
                lastNode.add(newNode);
            } else {
                Object userObject = lastNode.getUserObject();
                lastNode.setUserObject(deserializeTag((String)userObject, withColor));
                for (int i = currentLevel; i <= lastLevel; i++) {
                    lastNode = (DefaultMutableTreeNode) lastNode.getParent();
                    Object parentObject = lastNode.getUserObject();
                    if(parentObject instanceof String)
                        lastNode.setUserObject(deserializeTag((String)parentObject, false));
                }
                lastNode.add(newNode);
            }
            lastNode = newNode;
            lastLevel = currentLevel;
        }
        Object userObject = lastNode.getUserObject();
        lastNode.setUserObject(deserializeTag((String)userObject, withColor));
        for (int i = currentLevel; i <= lastLevel; i++) {
            lastNode = (DefaultMutableTreeNode) lastNode.getParent();
            Object parentObject = lastNode.getUserObject();
            if(parentObject instanceof String)
                lastNode.setUserObject(deserializeTag((String)parentObject, false));
        }
    }
    private Tag deserializeTag(String spec, boolean withColor) {
        if(withColor) {
            int colorIndex = spec.lastIndexOf("#");
            String content = spec.substring(0, colorIndex);
            String colorSpec = spec.substring(colorIndex);
            return registry.setTagColor(content, colorSpec);
        }
        else
            return registry.createTag(spec);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SimpleJTreeEditor().setVisible(true));
    }
}

class TreeTransferHandler extends TransferHandler {
    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath path = tree.getSelectionPath();
        if (path != null) {
            return new StringSelection(path.toString());
        }
        return null;
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport support) {
        if (!support.isDrop()) {
            return false;
        }
        support.setShowDropLocation(true);
        return support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
        int childIndex = dl.getChildIndex();
        TreePath dest = dl.getPath();
        JTree tree = (JTree) support.getComponent();
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode parent = (DefaultMutableTreeNode) dest.getLastPathComponent();
        JTree treeSource = (JTree) support.getComponent();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treeSource.getSelectionPath().getLastPathComponent();
        try {
            DefaultMutableTreeNode newNode = (DefaultMutableTreeNode) node.clone();
            if (childIndex == -1) {
                childIndex = parent.getChildCount();
            }
            model.insertNodeInto(newNode, parent, childIndex);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}


class MyNodeCellEditor extends AbstractCellEditor implements TreeCellEditor {

    private JTextField textField;
    private DefaultMutableTreeNode currentNode;
    private IconRegistry registry;


    public MyNodeCellEditor(IconRegistry registry) {
        this.registry = registry;
        textField = new JTextField();
        textField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stopCellEditing();
            }
        });

        textField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                stopCellEditing();
            }
        });
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value,
                                                boolean isSelected, boolean expanded,
                                                boolean leaf, int row) {
        currentNode = (DefaultMutableTreeNode) value;
        Tag tag = (Tag) currentNode.getUserObject();
        textField.setText(tag.getContent());
        return textField;
    }

    @Override
    public boolean isCellEditable(EventObject event) {
        if (event instanceof MouseEvent) {
            return ((MouseEvent) event).getClickCount() >= 2;
        }
        return true;
    }

    @Override
    public Object getCellEditorValue() {
        return registry.createTag(textField.getText());
    }

    @Override
    public boolean stopCellEditing() {
        fireEditingStopped();
        return true;
    }

    @Override
    public void cancelCellEditing() {
        fireEditingCanceled();
    }
}

class CustomLeafRenderer extends DefaultTreeCellRenderer {
    private Object rootNode; // Reference to the root node object

    public CustomLeafRenderer(DefaultMutableTreeNode rootNode) {
        this.rootNode = rootNode;
        setHorizontalAlignment(CENTER);
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean sel, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {


        super.getTreeCellRendererComponent(tree, null, sel, expanded, leaf, row, hasFocus);
        if (value instanceof DefaultMutableTreeNode) {
            Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
            if (userObject instanceof Tag) {
                Tag tag = (Tag) userObject;

                if (leaf && value != rootNode) {
                    setText(null);
                    setIcon(new TagIcon(tag, getFont())); // Example of setting a custom icon
                }
                else {
                    setText(tag.getContent());
                }
            }
            else if(userObject != null) {
                setText(userObject.toString());
            }
        }

        return this;
    }
}