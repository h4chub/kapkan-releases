package gg.kapkan.v23;

import java.awt.*;
import java.awt.event.*;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicTextFieldUI;

/** Small desktop components. No animation, extra images or background work. */
final class LauncherPolish {
    static final Color EDGE=new Color(255,255,255,13), FOCUS=new Color(238,166,178);
    static final Color HOVER=new Color(38,41,47), SELECTED=new Color(44,26,33);
    static boolean keyboardFocus;
    static void trackFocus(JFrame frame){
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e->{
            if(e.getID()==KeyEvent.KEY_PRESSED&&!keyboardFocus&&frame.isActive()
                &&java.util.Set.of(KeyEvent.VK_TAB,KeyEvent.VK_LEFT,KeyEvent.VK_RIGHT,KeyEvent.VK_UP,KeyEvent.VK_DOWN,KeyEvent.VK_ENTER,KeyEvent.VK_SPACE).contains(e.getKeyCode())){keyboardFocus=true;frame.repaint();}return false;
        });
        Toolkit.getDefaultToolkit().addAWTEventListener(e->{
            if(e.getID()==MouseEvent.MOUSE_PRESSED&&keyboardFocus){keyboardFocus=false;frame.repaint();}
        },AWTEvent.MOUSE_EVENT_MASK);
    }

    static class Surface extends KapKanH2Launcher.WidthColumn {
        Surface(LayoutManager layout){setLayout(layout);setOpaque(false);setAlignmentX(0);}
        protected void paintComponent(Graphics g){
            Graphics2D p=(Graphics2D)g.create();KapKanH2Launcher.quality(p);
            p.setColor(KapKanH2Launcher.PANEL);p.fillRoundRect(0,0,getWidth(),getHeight(),14,14);
            p.setColor(EDGE);p.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);p.dispose();
        }
    }

    static final class CheckIcon implements Icon {
        public int getIconWidth(){return 20;}public int getIconHeight(){return 20;}
        public void paintIcon(Component c,Graphics g,int x,int y){
            AbstractButton b=(AbstractButton)c;Graphics2D p=(Graphics2D)g.create();p.translate(x,y);KapKanH2Launcher.quality(p);
            p.setColor(b.isSelected()&&b.isEnabled()?KapKanH2Launcher.CRIMSON:KapKanH2Launcher.CONTROL);
            p.fillRoundRect(2,2,16,16,5,5);
            if(!b.isSelected()){p.setColor(new Color(116,123,136));p.drawRoundRect(2,2,16,16,5,5);}
            if(b.isSelected()){p.setColor(b.isEnabled()?KapKanH2Launcher.TEXT:KapKanH2Launcher.MUTED);p.setStroke(new BasicStroke(1.7f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));p.drawLine(6,10,9,13);p.drawLine(9,13,14,7);}
            if(b.hasFocus()&&keyboardFocus){p.setColor(FOCUS);p.setStroke(new BasicStroke(1.5f));p.drawRoundRect(0,0,19,19,7,7);}
            p.dispose();
        }
    }

    static final class TextFieldUI extends BasicTextFieldUI {
        protected void paintSafely(Graphics g){
            var c=getComponent();Graphics2D p=(Graphics2D)g.create();KapKanH2Launcher.quality(p);
            p.setColor(KapKanH2Launcher.CONTROL);p.fillRoundRect(0,0,c.getWidth(),c.getHeight(),10,10);
            p.setColor(EDGE);p.drawRoundRect(0,0,c.getWidth()-1,c.getHeight()-1,10,10);
            if(c.hasFocus()&&keyboardFocus)focus(p,c.getWidth(),c.getHeight(),10);
            p.dispose();super.paintSafely(g);
        }
    }

    static final class AccountButton extends JButton {
        String kind="Guest profile";
        boolean expanded;
        AccountButton(){
            KapKanH2Launcher.styleButton(this,false);setBorder(new EmptyBorder(5,12,5,12));
            setUI(new BasicButtonUI(){
                public void paint(Graphics g,JComponent c){paintAccount(g);}
            });
            getAccessibleContext().setAccessibleDescription("Open the account menu to switch profiles or manage accounts.");
        }
        public AccessibleContext getAccessibleContext(){
            if(accessibleContext==null)accessibleContext=new AccessibleJButton(){
                public AccessibleStateSet getAccessibleStateSet(){
                    var states=super.getAccessibleStateSet();states.add(expanded?AccessibleState.EXPANDED:AccessibleState.COLLAPSED);return states;
                }
            };
            return accessibleContext;
        }
        void update(String name,String type){
            setText(name);kind=type;
            setToolTipText(name+" · "+type+" · switch account");
            getAccessibleContext().setAccessibleName("Account: "+name+", "+type);
            revalidate();repaint();
        }
        void expanded(boolean value){
            boolean old=expanded;expanded=value;repaint();
            getAccessibleContext().firePropertyChange(AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                old?AccessibleState.EXPANDED:AccessibleState.COLLAPSED,
                value?AccessibleState.EXPANDED:AccessibleState.COLLAPSED);
        }
        public Dimension getPreferredSize(){
            int name=getFontMetrics(KapKanH2Launcher.FONT.deriveFont(Font.BOLD,14f)).stringWidth(getText());
            return new Dimension(Math.max(98,Math.min(202,name+54)),44);
        }
        void paintAccount(Graphics g){
            Graphics2D p=(Graphics2D)g.create();KapKanH2Launcher.quality(p);
            if(getModel().isRollover()||getModel().isPressed()){
                p.setColor(new Color(255,255,255,getModel().isPressed()?14:7));
                p.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
            }
            if(hasFocus()&&keyboardFocus)focus(p,getWidth(),getHeight(),10);
            p.setFont(KapKanH2Launcher.FONT.deriveFont(Font.BOLD,14f));
            p.setColor(isEnabled()?KapKanH2Launcher.TEXT:KapKanH2Launcher.MUTED);
            p.drawString(fit(getText(),p.getFontMetrics(),getWidth()-50),12,27);
            p.setColor(expanded?new Color(239,135,154):KapKanH2Launcher.MUTED);
            p.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            int x=getWidth()-21,y=23,d=expanded?-4:4;
            p.drawLine(x-4,y-d/2,x,y+d/2);p.drawLine(x,y+d/2,x+4,y-d/2);p.dispose();
        }
    }

    /** Transparent Swing popup; its shadow composites over the actual artwork. */
    static final class AccountMenu extends JPopupMenu {
        static final int SHADOW=8;
        static final Color BACKGROUND=new Color(27,29,34);
        boolean flatWindow;
        Window popupWindow;
        Color priorBackground;
        Shape priorShape;
        final java.util.Map<JComponent,Boolean> priorOpaque=new java.util.LinkedHashMap<>();
        AccountMenu(){
            setOpaque(false);setBackground(BACKGROUND);setLightWeightPopupEnabled(true);
            setBorder(new EmptyBorder(SHADOW+8,SHADOW+8,SHADOW+8,SHADOW+8));
        }
        public void show(Component invoker,int x,int y){
            // Keep the complete menu within the visible frame and monitor.
            Dimension size=getPreferredSize();Point point=invoker.getLocationOnScreen();point.translate(x,y);
            GraphicsConfiguration config=invoker.getGraphicsConfiguration();Rectangle safe=new Rectangle(config.getBounds());
            Insets inset=Toolkit.getDefaultToolkit().getScreenInsets(config);
            safe.x+=inset.left;safe.y+=inset.top;safe.width-=inset.left+inset.right;safe.height-=inset.top+inset.bottom;
            Window owner=SwingUtilities.getWindowAncestor(invoker);
            if(owner instanceof RootPaneContainer root){
                Container pane=root.getContentPane();Rectangle visible=new Rectangle(pane.getLocationOnScreen(),pane.getSize()).intersection(safe);
                if(visible.width>=size.width&&visible.height>=size.height)safe=visible;
            }
            point.x=Math.max(safe.x,Math.min(point.x,safe.x+safe.width-size.width));
            point.y=Math.max(safe.y,Math.min(point.y,safe.y+safe.height-size.height));
            Point origin=invoker.getLocationOnScreen();super.show(invoker,point.x-origin.x,point.y-origin.y);
            // Most menus stay lightweight. An overlapping owned window can require a native popup.
            Window window=SwingUtilities.getWindowAncestor(this);
            if(window!=null&&window!=owner){
                popupWindow=window;priorBackground=window.getBackground();priorShape=window.getShape();
                try{
                    if(config.getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)){
                        for(Container parent=getParent();parent!=null&&parent!=window;parent=parent.getParent())
                            if(parent instanceof JComponent component)priorOpaque.put(component,component.isOpaque());
                        window.setBackground(new Color(0,0,0,0));
                        priorOpaque.keySet().forEach(component->component.setOpaque(false));
                    }else{
                        flatWindow=true;window.setBackground(BACKGROUND);
                        if(config.getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))
                            window.setShape(new java.awt.geom.RoundRectangle2D.Double(0,0,window.getWidth(),window.getHeight(),24,24));
                    }
                }catch(IllegalArgumentException|IllegalComponentStateException|UnsupportedOperationException ignored){flatWindow=true;}
                repaint();
            }
        }
        public void setVisible(boolean visible){
            Window oldWindow=popupWindow;Color oldBackground=priorBackground;Shape oldShape=priorShape;
            super.setVisible(visible);
            if(!visible&&oldWindow!=null){
                // PopupFactory caches native windows; never leak this style into a later menu.
                try{oldWindow.setBackground(oldBackground);oldWindow.setShape(oldShape);}catch(IllegalArgumentException|IllegalComponentStateException|UnsupportedOperationException ignored){}
                priorOpaque.forEach(JComponent::setOpaque);priorOpaque.clear();
                popupWindow=null;flatWindow=false;
            }
        }
        protected void paintComponent(Graphics g){
            Graphics2D p=(Graphics2D)g.create();KapKanH2Launcher.quality(p);
            int pad=flatWindow?0:SHADOW,w=getWidth()-2*pad,h=getHeight()-2*pad;
            if(!flatWindow)for(int i=SHADOW;i>=1;i--){p.setColor(new Color(0,0,0,4+(SHADOW-i)*2));p.fillRoundRect(pad-i,pad-i+3,w+2*i,h+2*i,26+2*i,26+2*i);}
            p.setPaint(new GradientPaint(0,pad,new Color(31,33,39),0,pad+h,BACKGROUND));p.fillRoundRect(pad,pad,w,h,24,24);
            p.setColor(new Color(255,255,255,20));p.drawRoundRect(pad,pad,w-1,h-1,24,24);p.dispose();
        }
    }

    static JComponent menuDivider(){
        JComponent line=new JComponent(){
            protected void paintComponent(Graphics g){g.setColor(new Color(255,255,255,16));g.drawLine(10,getHeight()/2,getWidth()-11,getHeight()/2);}
        };
        line.setPreferredSize(new Dimension(284,17));line.setMaximumSize(new Dimension(Integer.MAX_VALUE,17));return line;
    }
    static JComponent menuHeading(String text){
        JLabel label=KapKanH2Launcher.label(text,Font.PLAIN,11,KapKanH2Launcher.MUTED);
        label.setBorder(new EmptyBorder(5,12,7,12));return label;
    }

    static final class AccountItem extends JMenuItem {
        final String kind;
        final boolean active;
        AccountItem(String name,String kind,boolean active,Runnable action){
            super(name);this.kind=kind;this.active=active;
            setFont(KapKanH2Launcher.FONT.deriveFont(Font.PLAIN,14f));setForeground(KapKanH2Launcher.TEXT);
            setBorder(new EmptyBorder(8,12,8,12));setOpaque(false);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(284,active?66:44));
            setUI(new BasicMenuItemUI(){
                public void paint(Graphics g,JComponent c){
                    Graphics2D p=(Graphics2D)g.create();KapKanH2Launcher.quality(p);
                    if(isArmed()||isSelected()){p.setColor(new Color(255,255,255,10));p.fillRoundRect(0,1,getWidth(),getHeight()-2,12,12);
                        if(keyboardFocus){p.setColor(FOCUS);p.drawRoundRect(1,2,getWidth()-3,getHeight()-5,11,11);}}
                    String type=kind.startsWith("Microsoft")?"Microsoft":"Guest";
                    if(active){
                        p.setFont(KapKanH2Launcher.FONT.deriveFont(Font.PLAIN,11f));p.setColor(KapKanH2Launcher.MUTED);
                        p.drawString("Playing as",12,19);p.drawString(type,getWidth()-12-p.getFontMetrics().stringWidth(type),19);
                        p.setFont(KapKanH2Launcher.FONT.deriveFont(Font.BOLD,17f));p.setColor(KapKanH2Launcher.TEXT);
                        p.drawString(fit(getText(),p.getFontMetrics(),getWidth()-56),12,45);
                        p.setColor(new Color(236,121,143));p.setStroke(new BasicStroke(1.8f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                        int x=getWidth()-27;p.drawLine(x,38,x+4,42);p.drawLine(x+4,42,x+11,33);
                    }else{
                        p.setFont(KapKanH2Launcher.FONT.deriveFont(Font.PLAIN,11f));p.setColor(KapKanH2Launcher.MUTED);
                        int typeWidth=p.getFontMetrics().stringWidth(type);p.drawString(type,getWidth()-12-typeWidth,27);
                        p.setFont(getFont());p.setColor(isEnabled()?KapKanH2Launcher.TEXT:KapKanH2Launcher.MUTED);
                        p.drawString(fit(getText(),p.getFontMetrics(),getWidth()-typeWidth-42),12,27);
                    }
                    p.dispose();
                }
            });
            getAccessibleContext().setAccessibleName(name+", "+kind+(active?", current account":""));
            addActionListener(e->action.run());
        }
    }

    static JMenuItem menuAction(String text,Runnable action){
        JMenuItem item=new JMenuItem(text);item.setFont(KapKanH2Launcher.FONT.deriveFont(13f));
        item.setForeground(KapKanH2Launcher.TEXT);item.setOpaque(false);
        item.setBorder(new EmptyBorder(12,12,12,12));item.setPreferredSize(new Dimension(284,44));
        item.setUI(new BasicMenuItemUI(){
            public void paint(Graphics g,JComponent c){
                Graphics2D p=(Graphics2D)g.create();KapKanH2Launcher.quality(p);
                if(item.isArmed()){p.setColor(new Color(255,255,255,10));p.fillRoundRect(0,1,c.getWidth(),c.getHeight()-2,12,12);
                    if(keyboardFocus){p.setColor(FOCUS);p.drawRoundRect(1,2,c.getWidth()-3,c.getHeight()-5,11,11);}}
                p.setColor(KapKanH2Launcher.TEXT);p.setFont(KapKanH2Launcher.FONT.deriveFont(Font.PLAIN,13f));p.drawString(text,12,27);
                p.setColor(KapKanH2Launcher.MUTED);p.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                int x=c.getWidth()-25;p.drawLine(x-3,22,x+7,22);p.drawLine(x+3,18,x+7,22);p.drawLine(x+7,22,x+3,26);p.dispose();
            }
        });
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));item.addActionListener(e->action.run());return item;
    }
    static void focus(Graphics2D p,int width,int height,int radius){
        p.setColor(FOCUS);p.setStroke(new BasicStroke(2));p.drawRoundRect(1,1,width-3,height-3,radius,radius);
    }
    static String fit(String value,FontMetrics fm,int width){
        if(fm.stringWidth(value)<=width)return value;
        int n=value.length();while(n>0&&fm.stringWidth(value.substring(0,n)+"…")>width)n--;
        return value.substring(0,n)+"…";
    }
}
