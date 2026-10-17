package gg.kapkan.v23;

import gg.kapkan.launcher.launch.*;
import gg.kapkan.launcher.export.VersionPackExporter;
import gg.kapkan.release.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.*;

/** Real desktop UI on the retained H1 engine, state store, accounts and installation paths. */
public final class KapKanH2Launcher {
    static final Color BG=new Color(15,17,20), PANEL=new Color(23,25,29), CONTROL=new Color(33,36,41);
    static final Color TEXT=new Color(242,243,245), MUTED=new Color(170,177,188), CRIMSON=new Color(201,38,63);
    static final Font FONT=KapKanV23Launcher.FONT;
    public static final String BUILD="client-release-1.0.5";
    static KapKanH2Launcher instance;
    final KapKanV23Launcher.App a;
    final LauncherUpdates updates;
    final LauncherPages content;
    final JPanel root=panel(new BorderLayout()), pages=panel(new CardLayout()), navigation=column();
    final Map<String,JButton> nav=new LinkedHashMap<>();
    final JLabel title=label("Play",Font.BOLD,22,TEXT), sub=label("Choose your version. Make it your world.",0,13,MUTED);
    final JLabel selected=label("",0,13,MUTED), state=label("Ready",Font.BOLD,13,TEXT), detail=label("",0,12,MUTED);
    final JLabel badgeState=label("",0,12,MUTED);
    final LauncherPolish.AccountButton profile=new LauncherPolish.AccountButton();
    JPopupMenu accountMenu;
    final JProgressBar progress=new JProgressBar();
    final JPanel accountRows=column();
    final DefaultListModel<String> mods=new DefaultListModel<>();
    final BadgeSettings badges=new BadgeSettings();
    volatile boolean preparing;
    volatile ModProfiles.Lease modLease;
    boolean updating;
    String currentPage="Play";
    BufferedImage artwork, logo;

    public static void main(String[] args){
        System.setProperty("awt.useSystemAAFontSettings","on");System.setProperty("swing.aatext","true");
        SwingUtilities.invokeLater(()->{theme();instance=new KapKanH2Launcher();instance.show();});
    }
    static void theme(){
        try{UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());}catch(Exception ignored){}
        for(String k:List.of("Panel.background","OptionPane.background","ScrollPane.background","Viewport.background"))UIManager.put(k,PANEL);
        for(String k:List.of("Label.foreground","OptionPane.messageForeground","CheckBox.foreground","TextField.foreground"))UIManager.put(k,TEXT);
        UIManager.put("TextField.background",CONTROL);UIManager.put("TextField.caretForeground",TEXT);
        UIManager.put("CheckBox.background",PANEL);UIManager.put("ScrollBar.width",10);
        UIManager.put("OptionPane.messageFont",FONT.deriveFont(14f));
        UIManager.put("Button.font",FONT.deriveFont(13f));
    }
    KapKanH2Launcher(){
        a=new KapKanV23Launcher.App();updates=new LauncherUpdates(this);content=new LauncherPages(this);
        root.setOpaque(true);root.setBackground(BG);
        try{
            artwork=a.scene.images.isEmpty()?a.scene.selected:a.scene.images.get(0);
            a.scene.images.clear(); // Release unused H1 preload images; keep the displayed artwork sharp.
            logo=ImageIO.read(KapKanH2Launcher.class.getResourceAsStream("/gg/kapkan/h2/double-k-white.png"));
        }catch(Exception e){throw new IllegalStateException("KapKan artwork is missing",e);}
        a.frame.setTitle("KapKan 1.0.5");a.frame.setIconImage(logo);a.frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        a.frame.setMinimumSize(new Dimension(820,560));a.frame.setContentPane(root);
        LauncherPolish.trackFocus(a.frame);
        buildNavigation();root.add(navigation,BorderLayout.WEST);
        JPanel body=panel(new BorderLayout(0,18));body.setBorder(new EmptyBorder(24,28,24,28));root.add(body);
        JPanel header=panel(new BorderLayout(16,0));JPanel headings=column();headings.add(title);headings.add(Box.createVerticalStrut(5));headings.add(sub);header.add(headings);
        profile.addActionListener(e->showAccountMenu());
        profile.getInputMap().put(KeyStroke.getKeyStroke("DOWN"),"openAccounts");
        profile.getActionMap().put("openAccounts",new AbstractAction(){public void actionPerformed(ActionEvent e){showAccountMenu();}});
        JPanel profileWrap=panel(new FlowLayout(FlowLayout.RIGHT,0,0));profileWrap.add(profile);header.add(profileWrap,BorderLayout.EAST);
        JPanel top=panel(new BorderLayout());top.add(header);top.add(updates.banner,BorderLayout.SOUTH);body.add(top,BorderLayout.NORTH);
        pages.add(playPage(),"Play");pages.add(modsPage(),"Mods");pages.add(accountsPage(),"Accounts");pages.add(explorePage(),"Explore");pages.add(settingsPage(),"Settings");body.add(pages);
        a.versionBox.addItem(a.state.version);wire();refreshAccounts();selectionChanged();a.loadVersions();select("Play");
        a.frame.getRootPane().setDefaultButton(a.playButton);
        bind("alt 1","Play",()->select("Play"));bind("alt 2","Mods",()->select("Mods"));bind("alt 3","Accounts",()->select("Accounts"));bind("alt 4","Explore",()->select("Explore"));bind("alt 5","Settings",()->select("Settings"));
        bind("F11","Fullscreen",this::fullscreen);
    }
    void show(){Rectangle bounds=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();a.frame.setSize(Math.min(1180,bounds.width),Math.min(760,bounds.height));a.frame.setLocationRelativeTo(null);a.frame.setVisible(true);content.clean(false);updates.start();}
    void fullscreen(){a.frame.setExtendedState(a.frame.getExtendedState()==Frame.MAXIMIZED_BOTH?Frame.NORMAL:Frame.MAXIMIZED_BOTH);}
    void bind(String key,String name,Runnable task){var r=a.frame.getRootPane();r.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),name);r.getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent e){task.run();}});}
    void buildNavigation(){
        navigation.setBackground(new Color(19,21,25));navigation.setPreferredSize(new Dimension(164,10));navigation.setBorder(new EmptyBorder(24,14,18,14));
        JPanel brand=panel(new FlowLayout(FlowLayout.LEFT,8,0));brand.add(new JLabel(new ImageIcon(logo.getScaledInstance(-1,34,Image.SCALE_SMOOTH))));brand.add(label("KapKan",Font.BOLD,18,TEXT));brand.setMaximumSize(new Dimension(136,48));brand.setAlignmentX(0);navigation.add(brand);navigation.add(Box.createVerticalStrut(35));
        for(String name:List.of("Play","Mods","Accounts","Explore")){JButton b=navButton(name);navigation.add(b);navigation.add(Box.createVerticalStrut(6));}
        navigation.add(Box.createVerticalGlue());navigation.add(navButton("Settings"));navigation.add(Box.createVerticalStrut(20));
        JLabel version=label("1.0.5",0,11,MUTED);version.setBorder(new EmptyBorder(0,12,0,0));navigation.add(version);
    }
    JButton navButton(String name){JButton b=button(name,false,()->select(name));b.putClientProperty("navigation",true);b.setIcon(new LineIcon(name));b.setIconTextGap(12);b.setHorizontalAlignment(SwingConstants.LEFT);b.setAlignmentX(0);b.setPreferredSize(new Dimension(136,44));b.setMinimumSize(new Dimension(136,44));b.setMaximumSize(new Dimension(136,44));nav.put(name,b);return b;}
    JPanel playPage(){
        JPanel page=panel(new BorderLayout(0,14));
        JPanel hero=new JPanel(new BorderLayout()){
            {setOpaque(false);setBorder(new EmptyBorder(22,24,24,24));}
            protected void paintComponent(Graphics g){Graphics2D p=(Graphics2D)g.create();quality(p);int w=getWidth(),h=getHeight();p.clip(new java.awt.geom.RoundRectangle2D.Float(0,0,w,h,12,12));
                double s=Math.max((double)w/artwork.getWidth(),(double)h/artwork.getHeight());int aw=(int)(artwork.getWidth()*s),ah=(int)(artwork.getHeight()*s);
                p.drawImage(artwork,(w-aw)/2,(h-ah)/2,aw,ah,null);
                p.setPaint(new GradientPaint(0,h*.36f,new Color(9,12,16,0),0,h,new Color(9,12,16,235)));p.fillRect(0,0,w,h);p.dispose();
            }
        };
        JPanel lower=column();lower.add(label("MINECRAFT JAVA",Font.BOLD,11,new Color(230,233,235)));lower.add(Box.createVerticalStrut(5));lower.add(label("A world of your own.",Font.BOLD,28,TEXT));lower.add(Box.createVerticalStrut(7));lower.add(selected);hero.add(lower,BorderLayout.SOUTH);page.add(hero);
        JPanel bottom=new LauncherPolish.Surface(null);bottom.setLayout(new BoxLayout(bottom,BoxLayout.Y_AXIS));bottom.setBorder(new EmptyBorder(18,20,14,20));
        JPanel fields=panel(new GridBagLayout());GridBagConstraints c=new GridBagConstraints();c.gridy=0;c.fill=GridBagConstraints.HORIZONTAL;c.weighty=1;
        combo(a.versionBox,"Minecraft version");combo(a.loaderBox,"Mod loader");combo(a.accountBox,"Minecraft account");
        JComboBox<?>[] boxes={a.versionBox,a.loaderBox,a.accountBox};String[] names={"Version","Loader","Account"};
        for(int i=0;i<3;i++){c.gridx=i;c.weightx=i==0?1.15:1;c.insets=new Insets(0,i==0?0:12,0,0);fields.add(field(names[i],boxes[i]),c);}
        styleButton(a.playButton,true);a.playButton.setText("Play");a.playButton.setIcon(new LineIcon("Play"));a.playButton.setIconTextGap(9);a.playButton.setPreferredSize(new Dimension(126,44));a.playButton.setMinimumSize(new Dimension(110,44));
        c.gridx=3;c.weightx=0;c.insets=new Insets(0,16,0,0);JPanel playField=field(" ",a.playButton);playField.setMinimumSize(new Dimension(126,68));playField.setPreferredSize(new Dimension(126,68));fields.add(playField,c);fields.setMaximumSize(new Dimension(Integer.MAX_VALUE,66));bottom.add(fields);bottom.add(Box.createVerticalStrut(16));
        JPanel status=panel(new BorderLayout(14,0));JPanel left=column();left.add(state);left.add(Box.createVerticalStrut(4));left.add(detail);status.add(left);JButton log=button("Launch log",false,this::showLog);log.putClientProperty("quiet",true);log.setIcon(new LineIcon("Log"));log.setIconTextGap(8);status.add(log,BorderLayout.EAST);status.setMaximumSize(new Dimension(Integer.MAX_VALUE,48));bottom.add(status);
        progress.setVisible(false);progress.setForeground(CRIMSON);progress.setBackground(CONTROL);progress.setBorderPainted(false);progress.setPreferredSize(new Dimension(100,3));progress.setMaximumSize(new Dimension(Integer.MAX_VALUE,3));bottom.add(Box.createVerticalStrut(8));bottom.add(progress);page.add(bottom,BorderLayout.SOUTH);return page;
    }
    void wire(){
        a.versionBox.addActionListener(e->{if(!updating)selectionChanged();});a.loaderBox.setSelectedItem(a.state.loader);a.loaderBox.addActionListener(e->{if(!updating)selectionChanged();});
        a.accountBox.addActionListener(e->{if(!updating&&a.accountBox.getSelectedItem() instanceof KapKanV23Launcher.AccountProfile p){a.state.active=p;a.state.save();updateProfile();renderAccounts();selectionChanged();}});
        a.topName.addPropertyChangeListener("text",e->{if(!updating)SwingUtilities.invokeLater(this::refreshAccounts);});
        a.playButton.addActionListener(e->launch());
    }
    void selectionChanged(){if(a.versionBox.getSelectedItem()==null)return;a.state.version=String.valueOf(a.versionBox.getSelectedItem());a.state.loader=String.valueOf(a.loaderBox.getSelectedItem());a.state.save();selected.setText(a.state.version+"   ·   "+a.state.loader);updateBadgeState();if(currentPage.equals("Mods"))refreshMods();if(!preparing&&!a.running){state.setText("Ready to play");detail.setText("Minecraft "+a.state.version+" · "+a.state.loader+" · "+a.state.active.name());}}
    void updateBadgeState(){badgeState.setText(!badges.enabled()?"Badges are off":badges.own()?"Your own badge is enabled":"Your own badge is hidden");}
    void updateProfile(){profile.update(a.state.active.name(),a.state.active.type()==KapKanV23Launcher.ProfileType.MICROSOFT?"Microsoft account":"Guest profile");}
    void refreshAccounts(){updating=true;try{a.refreshProfiles();updateProfile();renderAccounts();}finally{updating=false;}selectionChanged();}
    void useAccount(KapKanV23Launcher.AccountProfile p){if(preparing||a.running)return;a.state.active=p;a.state.save();refreshAccounts();}
    void showAccountMenu(){
        if(!profile.isEnabled())return;
        if(accountMenu!=null&&accountMenu.isVisible()){accountMenu.setVisible(false);return;}
        accountMenu=new LauncherPolish.AccountMenu();
        List<KapKanV23Launcher.AccountProfile> available=new ArrayList<>();available.add(a.state.active);
        for(var p:a.state.profiles)if(!p.equals(a.state.active))available.add(p);
        int shown=0;
        for(var p:available){if(shown==6)break;
            if(shown==1){accountMenu.add(LauncherPolish.menuDivider());accountMenu.add(LauncherPolish.menuHeading("Switch account"));}
            accountMenu.add(new LauncherPolish.AccountItem(p.name(),p.type()==KapKanV23Launcher.ProfileType.MICROSOFT?"Microsoft account":"Guest profile",p.equals(a.state.active),()->useAccount(p)));
            shown++;
        }
        accountMenu.add(LauncherPolish.menuDivider());
        accountMenu.add(LauncherPolish.menuAction(available.size()>6?"Manage all "+available.size()+" accounts":"Manage accounts",()->select("Accounts")));
        accountMenu.addPopupMenuListener(new javax.swing.event.PopupMenuListener(){
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e){profile.expanded(true);}
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e){profile.expanded(false);profile.requestFocusInWindow();}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e){profile.expanded(false);}
        });
        accountMenu.show(profile,profile.getWidth()-accountMenu.getPreferredSize().width+LauncherPolish.AccountMenu.SHADOW,profile.getHeight()-3);
        MenuSelectionManager.defaultManager().setSelectedPath(LauncherPolish.keyboardFocus
            ?new MenuElement[]{accountMenu,(MenuElement)accountMenu.getComponent(0)}:new MenuElement[]{accountMenu});
    }
    void renderAccounts(){content.renderAccounts();}
    void select(String name){currentPage=name;((CardLayout)pages.getLayout()).show(pages,name);title.setText(name);sub.setText(switch(name){case "Play"->"Choose your version. Make it your world.";case "Mods"->"Your installed mods and game files.";case "Accounts"->"Choose the name you play with.";case "Explore"->"A few personal touches for Minecraft.";default->"Make KapKan work for you.";});nav.forEach((n,b)->{boolean active=n.equals(name);b.putClientProperty("selected",active);b.setForeground(active?TEXT:MUTED);b.setFont(FONT.deriveFont(active?Font.BOLD:Font.PLAIN,13f));b.repaint();});if(name.equals("Mods"))refreshMods();if(name.equals("Accounts"))refreshAccounts();SwingUtilities.invokeLater(()->resetScroll(pages));}
    JPanel modsPage(){return content.mods();}
    void refreshMods(){content.refreshMods();}
    JPanel accountsPage(){return content.accounts();}
    JPanel explorePage(){return content.explore();}
    JPanel settingsPage(){return content.settings();}
    void launch(){
        if(preparing||a.running||a.versionBox.getSelectedItem()==null)return;
        String version=String.valueOf(a.versionBox.getSelectedItem()),loader=String.valueOf(a.loaderBox.getSelectedItem());var account=a.state.active;
        preparing=true;busy(true);state.setText("Preparing Minecraft");detail.setText("Checking installed files");a.playButton.setText("Preparing");a.logArea.setText("");log("Launch requested: "+version+" / "+loader+" / "+account.name());
        Consumer<String> report=line->{log(line);SwingUtilities.invokeLater(()->detail.setText(shorten(line,120)));};
        new SwingWorker<LaunchPlan,Void>(){
            protected LaunchPlan doInBackground()throws Exception {
                try(var ignored=LaunchProgress.listenDetailed(snapshot->SwingUtilities.invokeLater(()->showDownloadProgress(snapshot)))){
                    if(!FreshInstallSupport.isPrepared(a.mc,version)){report.accept("Preparing Minecraft "+version+"; verified downloads are reused");FreshInstallSupport.ensureFullyImported(a.mc,version,report);}
                    String resolved=a.loaders.ensureInstalled(a.mc,version,loader,report);
                    LaunchPlan plan=new LaunchPlanBuilder(a.mc).build(resolved,loader,account.name(),a.tune().memoryMiB());
                    if(account.type()==KapKanV23Launcher.ProfileType.MICROSOFT){report.accept("Refreshing Microsoft session");plan=a.withMicrosoft(plan,a.msa.sessionFor(account));}
                    var modProfile=content.modProfiles.resolve(version,loader);modLease=content.modProfiles.lock(modProfile);
                    plan=content.modProfiles.apply(plan,modProfile);report.accept("Mods folder: "+modProfile.mods());
                    KapKanV23Launcher.SplashManager.prepare(new MinecraftInstallation(plan.workingDirectory()),version,a.state);
                    return CompanionIntegration.prepare(plan,report);
                }
            }
            protected void done(){try{
                LaunchPlan plan=get();state.setText("Starting Minecraft");detail.setText("Opening the game process");
                a.processLauncher.launch(plan,KapKanH2Launcher.this::log,code->SwingUtilities.invokeLater(()->{
                    releaseModLease();a.running=false;busy(false);a.playButton.setText(code==0?"Play":"Retry");state.setText(code==0?"Ready to play":"Minecraft closed with an error");detail.setText(code==0?"Your game session has ended.":"Exit code "+code+". Open the launch log for details.");progress.setVisible(false);
                }));
                a.running=true;preparing=false;state.setText("Minecraft is running");detail.setText(version+" · "+loader+" · Launcher idle");a.playButton.setText("Running");progress.setVisible(false);
            }catch(Exception e){releaseModLease();preparing=false;a.running=false;busy(false);a.playButton.setText("Retry");state.setText("Couldn't launch Minecraft");String error=a.rootMessage(e);detail.setText(shorten(error,120));detail.setToolTipText(error);log("Launch failed: "+error);progress.setVisible(false);showLog();}}
        }.execute();
    }
    void showDownloadProgress(LaunchProgress.Snapshot snapshot){
        if(!preparing)return;
        state.setText(snapshot.stage().equals("Minecraft assets")?"Downloading Minecraft assets":snapshot.stage());
        detail.setText(snapshot.details());detail.setToolTipText(snapshot.description());
        int percent=snapshot.percent();progress.setVisible(true);progress.setIndeterminate(percent<0);
        if(percent>=0)progress.setValue(percent);
        progress.getAccessibleContext().setAccessibleDescription(snapshot.description());
    }
    void busy(boolean b){
        // Disabling Play transfers native focus to the next control. That is not keyboard navigation.
        if(b)LauncherPolish.keyboardFocus=false;
        a.versionBox.setEnabled(!b);a.loaderBox.setEnabled(!b);a.accountBox.setEnabled(!b);
        a.playButton.setIcon(b?null:new LineIcon("Play"));a.playButton.setEnabled(!b);profile.setEnabled(!b);
        if(b&&accountMenu!=null)accountMenu.setVisible(false);content.busyMods();updates.showPending();a.frame.repaint();
    }
    void releaseModLease(){if(modLease!=null){modLease.close();modLease=null;}}
    void log(String line){SwingUtilities.invokeLater(()->{a.logArea.append(line+"\n");if(a.logArea.getDocument().getLength()>150000)try{a.logArea.getDocument().remove(0,50000);}catch(Exception ignored){}a.logArea.setCaretPosition(a.logArea.getDocument().getLength());});}
    void showLog(){JDialog d=new JDialog(a.frame,"KapKan launch log",false);JPanel p=surface(new BorderLayout(0,12));a.logArea.setEditable(false);a.logArea.setFont(new Font(Font.MONOSPACED,0,12));a.logArea.setForeground(TEXT);a.logArea.setBackground(BG);a.logArea.setLineWrap(true);a.logArea.setWrapStyleWord(true);p.add(scroll(a.logArea));p.add(button("Close",false,d::dispose),BorderLayout.SOUTH);d.setContentPane(p);d.setSize(Math.min(850,a.frame.getWidth()-40),Math.min(500,a.frame.getHeight()-40));d.setLocationRelativeTo(a.frame);d.setVisible(true);}
    static String shorten(String s,int n){return s.length()>n?s.substring(0,n-1)+"…":s;}
    static JPanel panel(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);p.setAlignmentX(0);return p;}
    static JPanel column(){JPanel p=new WidthColumn();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setAlignmentX(0);return p;}
    static JPanel surface(LayoutManager layout){JPanel p=new LauncherPolish.Surface(layout);p.setBorder(new EmptyBorder(24,24,24,24));return p;}
    static JPanel scrollSurface(JPanel rows){JPanel p=surface(new BorderLayout());p.add(scroll(rows));return p;}
    static JLabel label(String text,int style,int size,Color color){JLabel l=new JLabel(text);l.setFont(FONT.deriveFont(style,(float)size));l.setForeground(color);l.setAlignmentX(0);return l;}
    static JTextArea note(String text){JTextArea n=new JTextArea(text);n.setFont(FONT.deriveFont(12f));n.setForeground(MUTED);n.setOpaque(false);n.setEditable(false);n.setLineWrap(true);n.setWrapStyleWord(true);n.setAlignmentX(0);n.setBorder(new EmptyBorder(6,0,10,10));n.setMaximumSize(new Dimension(Integer.MAX_VALUE,68));return n;}
    static void section(JPanel p,String name,String description){p.add(Box.createVerticalStrut(p.getComponentCount()==0?0:26));p.add(label(name,Font.BOLD,18,TEXT));p.add(Box.createVerticalStrut(6));p.add(note(description));}
    static JPanel field(String name,JComponent control){JPanel p=panel(new BorderLayout(0,7));JLabel l=label(name,0,12,MUTED);l.setLabelFor(control);p.add(l,BorderLayout.NORTH);p.add(control);p.setAlignmentX(0);p.setMinimumSize(new Dimension(80,68));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,68));return p;}
    static JCheckBox check(String text,boolean state){JCheckBox c=new JCheckBox(text,state);c.setIcon(new LauncherPolish.CheckIcon());c.setIconTextGap(8);c.setFocusPainted(false);c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));c.setOpaque(false);c.setForeground(TEXT);c.setFont(FONT.deriveFont(13f));c.setBorder(new EmptyBorder(8,0,8,0));c.setAlignmentX(0);return c;}
    static void textField(JTextField f){f.setUI(new LauncherPolish.TextFieldUI());f.setOpaque(false);f.setFont(FONT.deriveFont(14f));f.setBackground(CONTROL);f.setForeground(TEXT);f.setCaretColor(TEXT);f.setBorder(new EmptyBorder(12,12,12,12));f.setAlignmentX(0);f.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));}
    static JScrollPane scroll(Component c){JScrollPane s=new JScrollPane(c);s.setBorder(null);s.setOpaque(false);s.getViewport().setOpaque(false);s.getVerticalScrollBar().setUnitIncrement(24);s.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);s.getVerticalScrollBar().setUI(new DarkScroll());return s;}
    static JButton button(String text,boolean primary,Runnable action){JButton b=new JButton(text);styleButton(b,primary);if(action!=null)b.addActionListener(e->action.run());return b;}
    static void styleButton(JButton b,boolean primary){b.putClientProperty("primary",primary);b.setUI(new ButtonPainter());b.setFont(FONT.deriveFont(primary?Font.BOLD:Font.PLAIN,13f));b.setForeground(TEXT);b.setBorder(new EmptyBorder(12,14,12,14));b.setOpaque(false);b.setContentAreaFilled(false);b.setFocusPainted(false);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.setAlignmentX(0);b.setRolloverEnabled(true);}
    static void combo(JComboBox<?> b,String label){b.setUI(new ChoiceUI());b.setOpaque(false);b.setFont(FONT.deriveFont(14f));b.setForeground(TEXT);b.setBackground(CONTROL);b.setBorder(new EmptyBorder(2,8,2,4));b.setMinimumSize(new Dimension(80,44));b.setPreferredSize(new Dimension(150,44));b.setMaximumRowCount(8);b.getAccessibleContext().setAccessibleName(label);b.setRenderer(new ChoiceRenderer());b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));MouseAdapter hover=new MouseAdapter(){public void mouseEntered(MouseEvent e){b.putClientProperty("hover",true);b.repaint();}public void mouseExited(MouseEvent e){b.putClientProperty("hover",false);b.repaint();}};b.addMouseListener(hover);for(Component child:b.getComponents())child.addMouseListener(hover);}
    static void quality(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);}
    static void resetScroll(Container c){for(Component item:c.getComponents()){if(!item.isVisible())continue;if(item instanceof JScrollPane s)s.getViewport().setViewPosition(new Point(0,0));else if(item instanceof Container child)resetScroll(child);}}
    static class WidthColumn extends JPanel implements Scrollable{public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 24;}public int getScrollableBlockIncrement(Rectangle r,int o,int d){return Math.max(24,r.height-24);}public boolean getScrollableTracksViewportWidth(){return true;}public boolean getScrollableTracksViewportHeight(){return false;}}
    static class ButtonPainter extends BasicButtonUI {
        @Override protected void paintText(Graphics g,AbstractButton b,Rectangle bounds,String text){
            Graphics2D p=(Graphics2D)g.create();quality(p);p.setFont(b.getFont());
            p.setColor(b.isEnabled()?b.getForeground():MUTED);
            BasicGraphicsUtils.drawStringUnderlineCharAt(p,text,b.getDisplayedMnemonicIndex(),
                bounds.x+getTextShiftOffset(),bounds.y+p.getFontMetrics().getAscent()+getTextShiftOffset());p.dispose();
        }
        public void paint(Graphics g,JComponent c){
            AbstractButton b=(AbstractButton)c;Graphics2D p=(Graphics2D)g.create();quality(p);
            boolean primary=Boolean.TRUE.equals(b.getClientProperty("primary")),chosen=Boolean.TRUE.equals(b.getClientProperty("selected"));
            boolean quiet=Boolean.TRUE.equals(b.getClientProperty("navigation"))||Boolean.TRUE.equals(b.getClientProperty("quiet"));
            boolean hover=b.isEnabled()&&b.getModel().isRollover();
            Color bg=!b.isEnabled()?CONTROL:primary?(hover?new Color(215,43,70):CRIMSON):chosen?LauncherPolish.SELECTED:hover?LauncherPolish.HOVER:quiet?null:CONTROL;
            if(bg!=null){if(b.getModel().isPressed())bg=bg.darker();p.setColor(bg);p.fillRoundRect(0,0,c.getWidth(),c.getHeight(),10,10);}
            if(!quiet&&b.isEnabled()){p.setColor(primary?new Color(255,255,255,25):LauncherPolish.EDGE);p.drawRoundRect(0,0,c.getWidth()-1,c.getHeight()-1,10,10);}
            if(chosen){p.setColor(CRIMSON);p.fillRoundRect(0,14,3,c.getHeight()-28,3,3);}
            if(b.hasFocus()&&LauncherPolish.keyboardFocus)LauncherPolish.focus(p,c.getWidth(),c.getHeight(),10);
            p.dispose();super.paint(g,c);
        }
    }
    static class ChoiceRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){String name=value instanceof KapKanV23Launcher.AccountProfile p?p.name():Objects.toString(value,"Choose version");super.getListCellRendererComponent(list,name,index,selected,focus);setIcon(null);setFont(FONT.deriveFont(14f));setBorder(new EmptyBorder(index<0?0:11,8,index<0?0:11,8));setOpaque(index>=0);setBackground(selected?new Color(65,34,43):PANEL);setForeground(list.isEnabled()?TEXT:MUTED);setPreferredSize(new Dimension(120,index<0?36:40));return this;}
    }
    static class ChoiceUI extends BasicComboBoxUI {
        protected JButton createArrowButton(){JButton b=new JButton();b.setUI(new BasicButtonUI());b.setOpaque(false);b.setBorder(null);b.setContentAreaFilled(false);b.setPreferredSize(new Dimension(26,26));b.setIcon(new LineIcon("Chevron"));return b;}
        protected javax.swing.plaf.basic.ComboPopup createPopup(){return new BasicComboPopup(comboBox){
            {setBorder(BorderFactory.createLineBorder(new Color(58,62,70)));setBackground(PANEL);list.setBackground(PANEL);list.setForeground(TEXT);list.setFixedCellHeight(40);}
            public void show(){super.show();int index=comboBox.getSelectedIndex();if(index>=0){list.setSelectedIndex(index);list.ensureIndexIsVisible(index);}}
            protected JScrollPane createScroller(){JScrollPane s=super.createScroller();s.setBorder(null);s.setBackground(PANEL);s.getViewport().setBackground(PANEL);s.getVerticalScrollBar().setUI(new DarkScroll());s.getVerticalScrollBar().setPreferredSize(new Dimension(10,0));return s;}
        };}
        public void paint(Graphics g,JComponent c){Graphics2D p=(Graphics2D)g.create();quality(p);p.setColor(comboBox.isEnabled()&&Boolean.TRUE.equals(comboBox.getClientProperty("hover"))?LauncherPolish.HOVER:CONTROL);p.fillRoundRect(0,0,c.getWidth(),c.getHeight(),10,10);p.setColor(LauncherPolish.EDGE);p.drawRoundRect(0,0,c.getWidth()-1,c.getHeight()-1,10,10);if(comboBox.hasFocus()&&LauncherPolish.keyboardFocus)LauncherPolish.focus(p,c.getWidth(),c.getHeight(),10);p.dispose();super.paint(g,c);}
        public void paintCurrentValueBackground(Graphics g,Rectangle bounds,boolean focus){}
    }
    static class DarkScroll extends BasicScrollBarUI {
        protected void configureScrollBarColors(){thumbColor=new Color(81,87,97);trackColor=PANEL;}
        protected JButton createDecreaseButton(int o){return zero();}protected JButton createIncreaseButton(int o){return zero();}JButton zero(){JButton b=new JButton();b.setPreferredSize(new Dimension());return b;}
    }
    static class LineIcon implements Icon {
        final String name;LineIcon(String n){name=n;}public int getIconWidth(){return 18;}public int getIconHeight(){return 18;}
        public void paintIcon(Component c,Graphics g,int x,int y){Graphics2D p=(Graphics2D)g.create();p.translate(x,y);quality(p);boolean active=c instanceof JComponent b&&(Boolean.TRUE.equals(b.getClientProperty("primary"))||Boolean.TRUE.equals(b.getClientProperty("selected")));p.setColor(c.isEnabled()?(active?TEXT:MUTED):new Color(101,108,119));p.setStroke(new BasicStroke(1.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));switch(name){
            case "Play"->{p.drawPolygon(new int[]{5,14,5},new int[]{3,9,15},3);}case "Log"->{p.drawLine(3,4,7,8);p.drawLine(7,8,3,12);p.drawLine(10,13,16,13);}case "Mods"->{p.drawRect(3,3,5,5);p.drawRect(11,3,5,5);p.drawRect(3,11,5,5);p.drawRect(11,11,5,5);}case "Accounts"->{p.drawOval(6,2,6,6);p.drawArc(3,10,12,12,0,180);}case "Explore"->{p.drawOval(2,2,14,14);p.drawPolygon(new int[]{11,7,6,10},new int[]{5,7,12,10},4);}case "Settings"->{for(int yy:new int[]{4,9,14})p.drawLine(2,yy,16,yy);p.setColor(PANEL);p.fillRect(5,2,4,4);p.fillRect(10,7,4,4);p.fillRect(6,12,4,4);p.setColor(MUTED);p.drawRect(5,2,3,4);p.drawRect(10,7,3,4);p.drawRect(6,12,3,4);}default->{p.drawLine(5,7,9,11);p.drawLine(9,11,13,7);}}
            p.dispose();}
    }
}
