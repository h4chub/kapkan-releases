package gg.kapkan.v23;

import gg.kapkan.release.PerformanceIntegration;

import static gg.kapkan.v23.KapKanH2Launcher.*;
import gg.kapkan.release.LauncherMaintenance;
import gg.kapkan.release.ModProfiles;
import gg.kapkan.launcher.export.VersionPackExporter;
import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;

/** Inner pages share the existing launcher controls, accounts and game directory. */
final class LauncherPages {
    final KapKanH2Launcher ui;
    final LauncherMaintenance maintenance=new LauncherMaintenance();
    final JTextField modSearch=new JTextField();
    final JLabel modCount=label("Your installed files",Font.PLAIN,12,MUTED),accountCount=label("",Font.PLAIN,12,MUTED);
    final JLabel maintenanceStatus=label(maintenance.status(),Font.PLAIN,12,MUTED);
    final JPanel modBody=panel(new CardLayout());
    final JLabel emptyTitle=label("Your mods will appear here",Font.BOLD,17,TEXT);
    final JTextArea emptyText=note("Add compatible .jar files to your mods folder, then refresh.");
    final JButton cleanupButton=button("Clean old copies now",false,()->clean(true));
    final ModProfiles modProfiles;
    final JLabel modProfile=label("",Font.PLAIN,13,TEXT),modFolder=label("",Font.PLAIN,11,MUTED);
    final JButton chooseFolder=button("Choose folder",false,this::chooseModsFolder);
    final JButton defaultFolder=button("Use default",false,()->changeModsFolder(null));
    final JTextArea modHelp=note("Mods stay with the selected version and loader. Your original mods are kept.");
    ModProfiles.Selection modSelection;
    int modRefresh;
    boolean choosingFolder;
    List<String> allMods=List.of();
    boolean cleaning;
    JCheckBox ownBadge,enableBadges;
    LauncherPages(KapKanH2Launcher ui){this.ui=ui;modProfiles=new ModProfiles(ui.a.mc.gameDir(),ui.a.state.version,ui.a.state.loader);}

    static JPanel card(String title,String description){
        JPanel p=new LauncherPolish.Surface(null){public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};
        p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBorder(new EmptyBorder(20,22,20,22));
        p.add(label(title,Font.BOLD,17,TEXT));
        if(!description.isBlank()){p.add(Box.createVerticalStrut(3));p.add(note(description));}else p.add(Box.createVerticalStrut(14));
        return p;
    }
    static JPanel actions(JButton...buttons){
        JPanel p=panel(new FlowLayout(FlowLayout.LEFT,8,0));for(var b:buttons)p.add(b);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE,44));return p;
    }
    static void addCard(JPanel rows,JPanel card){if(rows.getComponentCount()>0)rows.add(Box.createVerticalStrut(16));rows.add(card);}
    static JPanel page(JPanel rows){JPanel p=panel(new BorderLayout());p.add(scroll(rows));return p;}
    static void changed(JTextField field,Runnable action){field.getDocument().addDocumentListener(new DocumentListener(){
        public void insertUpdate(DocumentEvent e){action.run();}public void removeUpdate(DocumentEvent e){action.run();}public void changedUpdate(DocumentEvent e){action.run();}
    });}

    JPanel mods(){
        JPanel p=surface(new BorderLayout(0,18)),header=column(),top=panel(new BorderLayout(12,0)),titles=column();
        titles.add(label("Mod collection",Font.BOLD,18,TEXT));titles.add(Box.createVerticalStrut(6));titles.add(modCount);top.add(titles);
        JPanel buttons=panel(new FlowLayout(FlowLayout.RIGHT,6,0));buttons.add(button("Refresh",false,this::refreshMods));buttons.add(chooseFolder);buttons.add(button("Open folder",true,this::openModsFolder));top.add(buttons,BorderLayout.EAST);header.add(top);header.add(Box.createVerticalStrut(12));
        chooseFolder.setToolTipText("Choose a custom mods folder for this version and loader");
        JPanel context=panel(new BorderLayout(8,0));JPanel location=column();location.add(modProfile);location.add(Box.createVerticalStrut(5));location.add(modFolder);context.add(location);
        defaultFolder.putClientProperty("quiet",true);defaultFolder.setVisible(false);context.add(defaultFolder,BorderLayout.EAST);header.add(context);header.add(Box.createVerticalStrut(12));
        textField(modSearch);modSearch.getAccessibleContext().setAccessibleName("Filter installed mods by filename");header.add(field("Find a mod",modSearch));changed(modSearch,this::filterMods);p.add(header,BorderLayout.NORTH);
        JList<String> list=new JList<>(ui.mods);list.setOpaque(false);list.setFixedCellHeight(64);list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Installed mod files");
        list.setCellRenderer((items,name,index,selected,focus)->{
            JPanel row=panel(new BorderLayout(12,0));row.setOpaque(true);row.setBackground(selected?CONTROL:PANEL);row.setBorder(new EmptyBorder(11,10,11,12));
            JLabel icon=new JLabel(new LineIcon("Mods"));row.add(icon,BorderLayout.WEST);JPanel words=column();
            JLabel filename=label(name,Font.PLAIN,13,TEXT);filename.putClientProperty("html.disable",true);filename.setToolTipText(name);words.add(filename);words.add(Box.createVerticalStrut(4));words.add(label("Installed in your mods folder",Font.PLAIN,11,MUTED));row.add(words);
            JLabel type=label("JAR",Font.BOLD,10,MUTED);row.add(type,BorderLayout.EAST);return row;
        });
        modBody.add(scroll(list),"list");JPanel empty=panel(new GridBagLayout()),words=column();words.add(emptyTitle);words.add(Box.createVerticalStrut(8));words.add(emptyText);empty.add(words);modBody.add(empty,"empty");p.add(modBody);
        p.add(modHelp,BorderLayout.SOUTH);filterMods();return p;
    }
    void refreshMods(){
        String version=ui.a.state.version,loader=ui.a.state.loader;int generation=++modRefresh;
        allMods=List.of();ui.mods.clear();modCount.setText("Reading folder…");modProfile.setText(version+" · "+loader);modFolder.setText(" ");
        new SwingWorker<ModScan,Void>(){
            protected ModScan doInBackground()throws Exception{var s=modProfiles.resolve(version,loader);try(var files=Files.list(s.mods())){return new ModScan(s,files.filter(Files::isRegularFile).map(p->p.getFileName().toString()).filter(n->n.toLowerCase(Locale.ROOT).endsWith(".jar")).sorted(String.CASE_INSENSITIVE_ORDER).toList());}}
            protected void done(){if(generation!=modRefresh)return;try{var scan=get();modSelection=scan.selection();allMods=scan.files();modProfile.setText(modSelection.label());modFolder.setText(shorten(modSelection.mods().toString(),70));modFolder.setToolTipText(modSelection.mods().toString());defaultFolder.setVisible(modSelection.custom());modHelp.setText(loader.equalsIgnoreCase("Vanilla")?"Vanilla does not load mods. Choose a mod loader on Play to use this feature.":"Mods stay with this version and loader. Change your selection on Play.");filterMods();}catch(Exception e){modSelection=null;ui.mods.clear();modCount.setText("Folder unavailable");defaultFolder.setVisible(true);emptyTitle.setText("Couldn't read this mods folder");emptyText.setText(ui.a.rootMessage(e));modHelp.setText("Reconnect your custom folder, choose another folder, or use the default.");((CardLayout)modBody.getLayout()).show(modBody,"empty");}}
        }.execute();
    }
    record ModScan(ModProfiles.Selection selection,List<String> files){}
    void busyMods(){chooseFolder.setEnabled(!choosingFolder&&!ui.preparing&&!ui.a.running);defaultFolder.setEnabled(chooseFolder.isEnabled());}
    void chooseModsFolder(){
        if(ui.preparing||ui.a.running||choosingFolder)return;
        JFileChooser chooser=new JFileChooser(modSelection==null?ui.a.mc.gameDir().toFile():modSelection.mods().toFile());
        chooser.setDialogTitle("Mods folder for "+ui.a.state.version+" · "+ui.a.state.loader);chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);chooser.setAcceptAllFileFilterUsed(false);
        if(chooser.showOpenDialog(ui.a.frame)==JFileChooser.APPROVE_OPTION)changeModsFolder(chooser.getSelectedFile().toPath());
    }
    void changeModsFolder(Path path){
        if(ui.preparing||ui.a.running||choosingFolder)return;
        String version=ui.a.state.version,loader=ui.a.state.loader;choosingFolder=true;busyMods();modHelp.setText("Saving folder…");
        new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{modProfiles.choose(version,loader,path);return null;}
            protected void done(){choosingFolder=false;busyMods();try{get();refreshMods();}catch(Exception e){modHelp.setText(ui.a.rootMessage(e));}}
        }.execute();
    }
    void openModsFolder(){
        String version=ui.a.state.version,loader=ui.a.state.loader;
        new SwingWorker<Path,Void>(){
            protected Path doInBackground()throws Exception{return modProfiles.resolve(version,loader).mods();}
            protected void done(){try{ui.a.openPath(get());}catch(Exception e){modHelp.setText(ui.a.rootMessage(e));}}
        }.execute();
    }
    void applyProfileAutoTune(){
        if(ui.preparing||ui.a.running)return;
        String version=ui.a.state.version,loader=ui.a.state.loader;
        new SwingWorker<Void,Void>(){
            protected Void doInBackground()throws Exception{
                var s=modProfiles.resolve(version,loader);try(var lease=modProfiles.lock(s)){
                    Path options=modProfiles.prepareGame(s).resolve("options.txt");var lines=Files.isRegularFile(options)?new ArrayList<>(Files.readAllLines(options)):new ArrayList<String>();var tune=ui.a.tune();
                    ui.a.setOption(lines,"renderDistance",String.valueOf(tune.renderDistance()));ui.a.setOption(lines,"simulationDistance",String.valueOf(tune.simulationDistance()));
                    ui.a.setOption(lines,"entityDistanceScaling",String.format(Locale.ROOT,"%.2f",tune.entityPercent()/100.0));ui.a.setOption(lines,"maxFps",String.valueOf(Math.max(60,tune.fps())));ui.a.setOption(lines,"enableVsync","false");Files.write(options,lines);
                }return null;
            }
            protected void done(){try{get();JOptionPane.showMessageDialog(ui.a.frame,"Suggested settings applied to "+version+" / "+loader+". Your controls are preserved.");}catch(Exception e){JOptionPane.showMessageDialog(ui.a.frame,ui.a.rootMessage(e),"Settings",JOptionPane.ERROR_MESSAGE);}}
        }.execute();
    }
    void filterMods(){
        String query=modSearch.getText().trim().toLowerCase(Locale.ROOT);ui.mods.clear();for(String name:allMods)if(name.toLowerCase(Locale.ROOT).contains(query))ui.mods.addElement(name);
        modCount.setText(query.isEmpty()?allMods.size()+" installed "+(allMods.size()==1?"mod":"mods"):ui.mods.size()+" of "+allMods.size()+" mods");
        emptyTitle.setText(allMods.isEmpty()?"Your mods will appear here":"No matching mods");emptyText.setText(allMods.isEmpty()?"Add compatible .jar files to your mods folder, then refresh.":"Try a shorter filename or clear the search.");
        ((CardLayout)modBody.getLayout()).show(modBody,ui.mods.isEmpty()?"empty":"list");
    }
    JPanel accounts(){
        JPanel rows=column(),intro=card("Saved profiles","Choose the profile to use the next time you launch Minecraft.");
        intro.add(actions(button("Add guest",true,()->{ui.a.addGuest();ui.refreshAccounts();}),button("Add Microsoft",false,ui.a::addMicrosoft)));
        intro.add(Box.createVerticalStrut(12));intro.add(accountCount);addCard(rows,intro);rows.add(Box.createVerticalStrut(16));rows.add(ui.accountRows);return page(rows);
    }
    void renderAccounts(){
        ui.accountRows.removeAll();accountCount.setText(ui.a.state.profiles.size()+" saved "+(ui.a.state.profiles.size()==1?"profile":"profiles"));
        for(var account:ui.a.state.profiles){
            boolean active=account.equals(ui.a.state.active);JPanel row=new LauncherPolish.Surface(new BorderLayout(16,0));row.setBorder(new EmptyBorder(18,22,18,22));row.setMaximumSize(new Dimension(Integer.MAX_VALUE,88));row.setPreferredSize(new Dimension(400,88));
            JPanel words=column(),name=panel(new FlowLayout(FlowLayout.LEFT,0,0));name.add(label(account.name(),Font.BOLD,16,TEXT));
            if(active){JLabel selected=label("   CURRENT",Font.BOLD,10,new Color(239,135,154));name.add(selected);}words.add(name);words.add(Box.createVerticalStrut(5));words.add(label(account.typeLabel(),Font.PLAIN,12,MUTED));row.add(words);
            if(active){JLabel current=label("In use",Font.PLAIN,12,MUTED);current.setBorder(new EmptyBorder(0,14,0,14));row.add(current,BorderLayout.EAST);}
            else{JButton use=button("Switch",false,()->ui.useAccount(account));use.setEnabled(!ui.preparing&&!ui.a.running);row.add(use,BorderLayout.EAST);}
            ui.accountRows.add(row);ui.accountRows.add(Box.createVerticalStrut(10));
        }
        ui.accountRows.revalidate();ui.accountRows.repaint();
    }
    JPanel explore(){
        JPanel rows=column(),splash=card("Your Minecraft splash","A personal line for the Minecraft title screen.");
        JTextArea preview=note(ui.a.state.splashText);preview.setFont(FONT.deriveFont(Font.BOLD,21f));preview.setForeground(new Color(235,203,117));preview.setBorder(new EmptyBorder(18,18,18,18));preview.setOpaque(true);preview.setBackground(BG);preview.setMaximumSize(new Dimension(Integer.MAX_VALUE,100));preview.setPreferredSize(new Dimension(400,80));splash.add(preview);splash.add(Box.createVerticalStrut(16));
        JCheckBox enabled=check("Enable custom splash",ui.a.state.splashEnabled);splash.add(enabled);
        JTextField text=new JTextField(ui.a.state.splashText);textField(text);splash.add(Box.createVerticalStrut(8));splash.add(field("Splash text",text));changed(text,()->preview.setText(text.getText().isBlank()?"KapKan was here":text.getText()));
        JLabel saved=label("",Font.PLAIN,12,MUTED);Runnable save=()->{ui.a.state.splashEnabled=enabled.isSelected();ui.a.state.splashText=text.getText().isBlank()?"KapKan was here":text.getText().trim();ui.a.state.save();saved.setText("Splash saved.");};
        splash.add(Box.createVerticalStrut(16));splash.add(actions(button("Save splash",true,save),button("Use on next launch",false,()->{enabled.setSelected(true);ui.a.state.forceSplash=true;save.run();saved.setText("Saved for your next launch.");})));splash.add(Box.createVerticalStrut(10));splash.add(saved);addCard(rows,splash);
        JPanel timing=card("A small surprise","When enabled, your line appears on 25% of launches. Minecraft's normal splash text appears the rest of the time.");addCard(rows,timing);return page(rows);
    }
    JPanel settings(){
        JPanel rows=column();addCard(rows,ui.updates.settings());
        JPanel badges=card("Badges & capes","Your KapKan appearance in Fabric 26.1, 26.1.1 and 26.1.2.");
        badges.add(ui.badgeState);badges.add(Box.createVerticalStrut(8));enableBadges=check("Enable badges and cape sharing",ui.badges.enabled());ownBadge=check("Show my own badge",ui.badges.own());
        badges.add(enableBadges);badges.add(ownBadge);badges.add(note("Your own badge works offline, in Tab and above your character in third person."));
        JLabel saved=label("Changes save automatically.",Font.PLAIN,12,MUTED);
        Runnable saveToggles=()->{try{ui.badges.save(enableBadges.isSelected(),ownBadge.isSelected(),ui.badges.endpoint());ui.updateBadgeState();saved.setText("Saved. Applies in game within 5 seconds.");}catch(Exception e){saved.setText("Couldn't save: "+e.getMessage());}};
        enableBadges.addActionListener(e->saveToggles.run());ownBadge.addActionListener(e->saveToggles.run());badges.add(saved);
        JPanel remote=column();remote.add(note("On supported Fabric servers, install the matching KapKan mod on the server and each client. Sharing then works automatically. An optional HTTP service can be configured below."));JTextField url=new JTextField(ui.badges.endpoint());textField(url);remote.add(field("Presence service URL",url));remote.add(Box.createVerticalStrut(12));
        remote.add(button("Save service URL",false,()->{try{ui.badges.save(enableBadges.isSelected(),ownBadge.isSelected(),url.getText());ui.updateBadgeState();saved.setText(url.getText().isBlank()?"Service cleared. Your own badge still works.":"Service saved; connection is checked in game.");}catch(Exception e){saved.setText(e.getMessage());}}));
        remote.add(note("Shares your equipped cape PNG, profile UUID/name and a hashed server address. Turning sharing off stops uploads and lookups."));
        badges.add(Box.createVerticalStrut(14));badges.add(disclosure("Server relay & optional service",remote));addCard(rows,badges);

        JPanel performance=card("Game performance","Suggested starting settings for your detected hardware.");var tune=ui.a.tune();JPanel stats=panel(new GridLayout(1,3,16,0));stats.add(stat("GAME MEMORY",String.format(Locale.ROOT,"%.1f GB",tune.memoryMiB()/1024.0)));stats.add(stat("VIEW DISTANCE",tune.renderDistance()+" chunks"));stats.add(stat("FPS LIMIT",String.valueOf(tune.fps())));stats.setMaximumSize(new Dimension(Integer.MAX_VALUE,58));performance.add(stats);performance.add(Box.createVerticalStrut(16));
        JCheckBox modules=check("Use rendering optimizations",PerformanceIntegration.enabled());
        JLabel moduleStatus=label("Applies when Minecraft next starts.",Font.PLAIN,12,MUTED);
        modules.addActionListener(e->{try{PerformanceIntegration.setEnabled(modules.isSelected());moduleStatus.setText("Saved. Applies when Minecraft next starts.");}catch(Exception error){modules.setSelected(PerformanceIntegration.enabled());moduleStatus.setText("Couldn't save this preference.");}});
        performance.add(modules);performance.add(moduleStatus);performance.add(note("Turn off for graphics-mod compatibility. Changes apply the next time Minecraft starts."));performance.add(Box.createVerticalStrut(12));
        performance.add(button("Apply suggested settings",false,this::applyProfileAutoTune));performance.add(note("Applies to the selected version and loader. Your controls are preserved."));addCard(rows,performance);

        JPanel tidy=card("Keep KapKan tidy","Remove old launcher apps, ZIPs and extracted downloads. Your game files stay in place.");
        JCheckBox automatic=check("Clean old launcher copies after startup",maintenance.automatic());automatic.addActionListener(e->new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{maintenance.automatic(automatic.isSelected());return null;}protected void done(){try{get();maintenanceStatus.setText("Cleanup preference saved.");}catch(Exception error){maintenanceStatus.setText("Couldn't save cleanup preference.");}}}.execute());tidy.add(automatic);tidy.add(note("Verified older Mac packages go to Trash. The current app, running copies, mods, worlds, accounts and settings are protected."));tidy.add(cleanupButton);tidy.add(Box.createVerticalStrut(10));tidy.add(maintenanceStatus);addCard(rows,tidy);

        JPanel files=card("Game files",ui.a.mc.gameDir().toString());files.add(actions(button("Open game folder",false,()->ui.a.openPath(ui.a.mc.gameDir())),button("Export version packs",false,()->VersionPackExporter.open(ui.a.frame,ui.a.mc.gameDir()))));addCard(rows,files);
        JPanel versions=card("Versions","Choose which Minecraft releases appear in the version menu.");JCheckBox snapshots=check("Show snapshots and pre-releases",ui.a.state.showSnapshots);snapshots.addActionListener(e->{ui.a.state.showSnapshots=snapshots.isSelected();ui.a.state.save();ui.a.loadVersions();});versions.add(snapshots);addCard(rows,versions);
        JPanel advanced=column();advanced.add(note(ui.a.state.microsoftClientId.isBlank()?"Microsoft sign-in needs a configured OAuth client ID.":"Microsoft OAuth client ID is configured locally."));advanced.add(button("Set client ID",false,()->{String value=JOptionPane.showInputDialog(ui.a.frame,"KapKan Microsoft OAuth Client ID:",ui.a.state.microsoftClientId);if(value!=null){ui.a.state.microsoftClientId=value.trim();ui.a.state.save();}}));
        JPanel about=card("KapKan 1.0.7","");about.add(disclosure("Advanced sign-in settings",advanced));about.add(note("Interface motion is disabled. KapKan remains idle while Minecraft runs."));addCard(rows,about);ui.updateBadgeState();return page(rows);
    }
    static JPanel stat(String caption,String value){JPanel p=column();p.add(label(caption,Font.BOLD,10,MUTED));p.add(Box.createVerticalStrut(7));p.add(label(value,Font.BOLD,20,TEXT));return p;}
    static JPanel disclosure(String title,JPanel content){
        JPanel p=column();content.setVisible(false);JButton toggle=button(title+"  +",false,null);toggle.putClientProperty("quiet",true);
        toggle.addActionListener(e->{boolean open=!content.isVisible();content.setVisible(open);toggle.setText(title+(open?"  −":"  +"));p.revalidate();p.repaint();});p.add(toggle);p.add(content);return p;
    }
    void clean(boolean manual){
        if(cleaning||!manual&&(System.getProperty("kapkan.nativeBootstrap","").isBlank()||!maintenance.automatic()))return;
        cleaning=true;cleanupButton.setEnabled(false);maintenanceStatus.setText("Checking old launcher copies…");
        new SwingWorker<String,Void>(){protected String doInBackground()throws Exception{return maintenance.clean(ui.a.mc.gameDir(),manual);}protected void done(){try{maintenanceStatus.setText(get());}catch(Exception e){maintenanceStatus.setText("Cleanup couldn't finish. Existing files were kept where possible.");}finally{cleaning=false;cleanupButton.setEnabled(true);}}}.execute();
    }
}
