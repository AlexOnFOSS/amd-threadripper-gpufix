/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * Try to recover Zen's PCIe bridge after a secondary bus 
 * reset kicks it out of whack.
 * 
 * @author HyenaCheeseHeads
 */
public class ZenBridgeBaconRecovery {
    public static void log(String text){
        System.out.println(new Date() + ": "+text);
    }
    
    public static void main(String[] args) throws IOException, InterruptedException {        
        System.out.println("-------------------------------------------");
        System.out.println("Zen PCIe-Bridge BAR/Config Recovery Tool, rev 1, 2018, HyenaCheeseHeads");
        System.out.println("-------------------------------------------");
        HashMap<Path, Path> bridgeMapping = new HashMap<>();
        
        File vfioDir = new File("/sys/bus/pci/drivers/vfio-pci");
        if (!vfioDir.exists()){
            log("!!! Cannot find the VFIO-PCI sysfs at "+vfioDir);
            System.exit(-1);
        }
        if (!(vfioDir.canRead() && vfioDir.canWrite())){
            log("!!! This tool requires R/W access to the VFIO-PCI sysfs at "+vfioDir);
            log("!!! Make sure to run it as root (or similar super user)");
            System.exit(-1);
        }

        // Detect list of devices using VFIO-PCI sysfs entry
        log("Detecting VFIO-PCI devices");
        for (File f : vfioDir.listFiles()){            
            if (f.isDirectory() && f.getName().startsWith("00")){
                Path devicePath = f.toPath().toRealPath();
                log("\tDevice: "+devicePath);
                
                try {
                    Path bridgePath = devicePath.getParent();
                    byte[] id = new byte[4];
                    bridgePath.resolve("config").toUri().toURL().openStream().read(id);
                    if (id[0]==0x22 && id[1]==0x10 && id[2]==0x53 && id[3]==0x14){
                        log("\t\tBridge: "+bridgePath);
                        bridgeMapping.put(devicePath, bridgePath);
                    } else {
                        log("\t\t!!! Unknown bridge type! Skipping...");
                    }
                } catch (IOException ex){
                    log("\t\t!!! Exception: "+ex.getMessage());
                    log("\t\t!!! Skipping...");
                }
            }
        }
        
        // Monitor devices for bridge failure pattern
        log("Monitoring "+bridgeMapping.size()+" device(s)...");
        while (true){
            for (Entry<Path,Path> bridgeEntry : bridgeMapping.entrySet()){
                try (FileInputStream deviceConfig = new FileInputStream(bridgeEntry.getKey().resolve("config").toFile())){                    
                    byte[] id = new byte[4];
                    deviceConfig.read(id);
                    if (id[0]==-1 && id[1]==-1){
                        // Failure detected, recover bridge by rewriting its config
                        log("Lost contact with "+bridgeEntry.getKey());
                        byte[] data = new byte[512];
                        try (
                                FileInputStream bridgeConfigIn = new FileInputStream(bridgeEntry.getValue().resolve("config").toFile());
                                FileOutputStream bridgeConfigOut = new FileOutputStream(bridgeEntry.getValue().resolve("config").toFile());
                                ){                                                
                            log("\tRecovering "+bridgeConfigIn.read(data)+" bytes");
                            bridgeConfigOut.write(data);
                            log("\tBridge config write complete");
                        } catch (IOException ex){
                            log("\t!!! Exception: "+ex.getMessage());
                            Thread.sleep(10000);
                        }
                        try (FileInputStream deviceRecoveryConfig = new FileInputStream(bridgeEntry.getKey().resolve("config").toFile())){                    
                            deviceConfig.read(id);
                            if (id[0]==-1 && id[1]==-1){
                                log("\tFailed to recover bridge secondary bus");
                            } else {
                                log("\tRecovered bridge secondary bus");
                                log("Re-acquired contact with "+bridgeEntry.getKey());
                            }
                        }
                    }
                }
            }
            Thread.sleep(100);
        }
    }
    
}