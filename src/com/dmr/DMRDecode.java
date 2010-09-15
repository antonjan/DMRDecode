// Please note much of the code in this program was taken from the DSD software
// and converted into Java. The author of this software is unknown but has the
// GPG Key ID below

// Copyright (C) 2010 DSD Author
// GPG Key ID: 0x3F1D7FD0 (74EF 430D F7F2 0A48 FCE6  F630 FAA2 635D 3F1D 7FD0)
// 
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
//
// THE SOFTWARE IS PROVIDED "AS IS" AND ISC DISCLAIMS ALL WARRANTIES WITH
// REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
// AND FITNESS.  IN NO EVENT SHALL ISC BE LIABLE FOR ANY SPECIAL, DIRECT,
// INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
// LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE
// OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
// PERFORMANCE OF THIS SOFTWARE.
//

package com.dmr;

import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import java.io.FileWriter;
import javax.swing.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.Element;
import javax.swing.text.html.HTML;
import javax.swing.text.StyleConstants;
import javax.swing.JEditorPane;
import java.text.DateFormat;
import java.util.Date;

public class DMRDecode {
	private DisplayModel display_model;
	private DisplayView display_view;
	private static DMRDecode theApp;
	static DisplayFrame window;
	public String program_version="DMR Decoder V0.00 Build 1";
	public TargetDataLine Line;
	public AudioFormat format;
	public int vertical_scrollbar_value=0;
	public int horizontal_scrollbar_value=0;
	private boolean audioReady=false;
	private static boolean RUNNING=true;
	private final int samplesPerSymbol=10;
	private int jitter=-1;
	private final int symbolCenter=4;
	private int max=15000;
	private int min=-15000;
	private int center=0;
	private int lastsample=0;
	private int maxref=12000;
	private int minref=-12000;
	private int lastsynctype=-1;
	private int symbolcnt=0;
	private static final int DMR_DATA_SYNC[]={3,1,3,3,3,3,1,1,1,3,3,1,1,3,1,1,3,1,3,3,1,1,3,1};
	private static final int DMR_VOICE_SYNC[]={1,3,1,1,1,1,3,3,3,1,1,3,3,1,3,3,1,3,1,1,3,3,1,3};
	private boolean carrier=false;
	public boolean inverted_dmr=false;
	private boolean firstframe=false;
	public JEditorPane editorPane;
	public HTMLDocument doc;
	public Element el;
	private int lmid=0;
	private int umid=0;
	private int synctype;
	private int dibit_buf[]=new int[144];
	private boolean frameSync=false;
	public boolean saveToFile=false;
	public FileWriter file;
	public boolean logging=false;
	
	
	public static void main(String[] args) {
		theApp=new DMRDecode();
		SwingUtilities.invokeLater(new Runnable(){public void run(){theApp.createGUI();}});
		theApp.prepare_audio();
		while (RUNNING)	{
			if (theApp.audioReady==true) theApp.decode();
		}

		}
	
	// Setup the window //
	public void createGUI() {
		window=new DisplayFrame(program_version,this);	
		Toolkit theKit=window.getToolkit();
		Dimension wndsize=theKit.getScreenSize();
		window.setBounds(wndsize.width/6,wndsize.height/6,2*wndsize.width/3,2*wndsize.height/3);
		window.addWindowListener(new WindowHandler());
		display_model=new DisplayModel();
		editorPane=new JEditorPane();
		editorPane.setContentType("text/html");
		editorPane.setEditable(false);
		editorPane.setText("<html><table border=\"0\" cellspacing=\"0\" cellpadding=\"0\"></table></html>");
	    doc=(HTMLDocument)editorPane.getDocument();
		el=doc.getElement(doc.getDefaultRootElement(),StyleConstants.NameAttribute,HTML.Tag.TABLE);
		JScrollPane scrollPane=new JScrollPane(editorPane);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		window.getContentPane().add(scrollPane,BorderLayout.CENTER);
		window.setVisible(true);
		}

	class WindowHandler extends WindowAdapter {
		public void windowClosing(WindowEvent e) {	
			}
		}

	public DisplayFrame getWindow()	{
		return window;	
		}

	public DisplayModel getModel() {
		return display_model;
		}

	public DisplayView getView() {
		return display_view;	
		}
	

	// Setup the audio interface
	public void prepare_audio() {
		  try {
			  format=new AudioFormat(48000,16,1,true,true);
			  DataLine.Info info=new DataLine.Info(TargetDataLine.class,format);
			  Line=(TargetDataLine) AudioSystem.getLine(info);
			  Line.open(format);
			  Line.start();
			  audioReady=true;
		  } catch (Exception e) {
			  JOptionPane.showMessageDialog(null, "Fatal error in prepare_audio","DMRdecoder", JOptionPane.ERROR_MESSAGE);
			  System.exit(0);
	   		}
	   	}
	  
	// The main routine for decoding DMR data
	public void decode()	{
		  noCarrier();
		  synctype=getFrameSync();
	      center=(max+min)/2;
	      umid=((max-center)*5/8)+center;
	      lmid=((min-center)*5/8)+center;
	      while (synctype!=-1)	{
	          processFrame();
	          synctype=getFrameSync();
		      center=(max+min)/2;
		      umid=((max-center)*5/8)+center;
		      lmid=((min-center)*5/8)+center;    		      
	        }  
	  }
	  
	// This code lifted straight from the DSD source code converted to Java and tidied up removing non DMR code
	public int getSymbol(boolean have_sync)	{
		  int sample,i,sum=0,symbol,count=0;
		  for (i=0;i<samplesPerSymbol;i++)	{
		      // timing control
		      if ((i==0)&&(have_sync==false))	{
		        if ((jitter>0)&&(jitter<=symbolCenter)) i--;          
		         else if ((jitter>symbolCenter)&&(jitter<samplesPerSymbol)) i++;          
		        jitter=-1;
		       }
			  sample=getAudio();
			  if ((sample>max)&&(have_sync==true)) sample=max;  
			   else if ((sample<min)&&(have_sync==true)) sample=min;
		      if (sample>center)	{
		    	  if ((jitter<0)&&(lastsample<center)&&(sample<(maxref*1.25))) jitter=i;   
		        }
		      else	{                       
		        if (sample<(minref*1.25))	{
		            if (jitter<0) jitter=i;
		            }
		          else	{
		            if ((jitter<0)&&(lastsample>center)) jitter=i;   
		            }
		        } 
		      if (((i>=symbolCenter-1)&&(i<=symbolCenter+2))||((i==symbolCenter)||(i==symbolCenter+1))) 	{
		    	  sum+=sample;
		          count++;
		          }
		      lastsample=sample;
		    }
		  symbol=(sum/count);
		  symbolcnt++;
		  return symbol;
	  }
	  
	  // Grab and return a single byte from the audio line
	  public int getAudio ()	{
		  int sample,count,total=0;
		  byte buffer[]=new byte[2];
		  try	{
			  while (total<1)	{
				  count=Line.read(buffer,0,2);
				  total=total+count;
			  	}
			  } catch (Exception e)	{
			  String err=e.getMessage();
			  JOptionPane.showMessageDialog(null,err,"DMRDecode", JOptionPane.ERROR_MESSAGE);
		  }
		  sample=(buffer[0]<<8)+buffer[1];
		  return sample;
	  }
	  
	public int getFrameSync ()	{

		int i,t=0,dibit,symbol,synctest_pos=0,lastt=0;
		int lmin=0,lmax=0,lidx=0;
		int lbufCount;
		boolean dataSync=false;
		boolean voiceSync=false;
		Quicksort qsort=new Quicksort();
		
		// Buffer size
		if (frameSync==true) lbufCount=144;
		 else lbufCount=23;
		int lbuf[]=new int[lbufCount];
		int lbuf2[]=new int[lbufCount];
		
		while (true) {
			t++;
			symbol=getSymbol(frameSync);
			lbuf[lidx]=symbol;
			if (lidx==(lbufCount-1)) lidx=0;
			 else lidx++;

			// Get the dibit state
			if (frameSync==false) {
				if (lastt==lbufCount) {
					lastt=0;
				}
				else lastt++;
				if (inverted_dmr==false)	{
					// Sync Normal
					if (symbol>0) dibit=1;
					else dibit=3;
				}
				
				else	{
					// Sync Inverted
					if (symbol>0) dibit=3;
					else dibit=1;
				}
			}
			else {
				if (inverted_dmr==false)	{
					// Frame Normal
					if (symbol>center) {
						if (symbol>umid) dibit=1;
						else dibit=0;
					}
					else {
						if (symbol<lmid) dibit=3;
						else dibit=2;
					}
				}
				else	{	
					// Frame Inverted
					if (symbol>center) {
						if (symbol>umid) dibit=3;
						else dibit=2;
					}
					else {
						if (symbol<lmid) dibit=1;
						else dibit=0;
					}
				}	
			}
				
			addToDitbitBuf(dibit);
		
			if (t>=lbufCount) {
				for (i=0;i<lbufCount;i++) {
					lbuf2[i]=lbuf[i];
				}
				qsort.sort(lbuf2);
				lmin=(lbuf2[2]+lbuf2[3]+lbuf2[4])/3;
				lmax=(lbuf2[lbufCount-4]+lbuf2[lbufCount-3]+lbuf2[lbufCount-2])/3;
				maxref=max;
				minref=min;
				// Check if this has a valid voice or data frame sync
				dataSync=syncCompare(DMR_DATA_SYNC);
				voiceSync=syncCompare(DMR_VOICE_SYNC);
				// Data frame
				if (dataSync==true) {
					carrier=true;
					frameSync=true;
					max=(max+lmax)/2;
					min=(min+lmin)/2;
					if (lastsynctype==-1) firstframe=true;
					 else firstframe=false;
					lastsynctype=10;
					return (10);
				}
				// Voice frame
				if (voiceSync==true) {
					carrier=true;
					frameSync=true;
					max=(max+lmax)/2;
					min=(min+lmin)/2;
					if (lastsynctype==-1) firstframe=true;
					 else firstframe=false;
					lastsynctype=12;
					return (12);
				}
		}
			
		if ((synctest_pos==144)&&(lastsynctype!=-1)) {
			if ((lastsynctype==10)&&(voiceSync==false)) {
				//carrier=true;
				//frameSync=false;
				//max=((max)+lmax)/2;
				//min=((min)+lmin)/2;
				//lastsynctype=-1;
				//return (11);
			}
			else if ((lastsynctype==12)&&(dataSync==false)) {
				//carrier=true;
				//frameSync=false;
				//max=((max)+lmax)/2;
				//min=((min)+lmin)/2;
				//lastsynctype=-1;
				//return (12);
			}
		}
		
			
		// We had a signal but appear to have lost it
		if (carrier==true) {
			if (synctest_pos>=1800) {
				addLine(getTimeStamp()+" Carrier Lost !");
				frameSync=false;
				noCarrier();
				return (-1);
				}
			}

		if (t>32000) {
			// buffer reset
			t=0;
			synctest_pos=0;
			}
		else synctest_pos++;

		}
	  }
	  
	// Add a dibit to the dibit buffer
	void addToDitbitBuf (int dibit)	{
		int a;
		// Rotate the dibit buffer to the left
		for (a=0;a<143;a++)	{
			dibit_buf[a]=dibit_buf[a+1];
		}
		dibit_buf[143]=dibit;
	}
	
	void noCarrier ()	{
		jitter=-1;
		lastsynctype=-1;
		carrier=false;
		max=15000;
		min=-15000;
		center=0;
		firstframe=false;
	  	}
	  
	// Compare the sync held in an array with the contents of the dibit_buf
	public boolean syncCompare(int c[])	{
		int i;
		for (i=0;i<24;i++)	{
			if (dibit_buf[i+66]!=c[i]) return false;
		}
		return true;
	}
	  
	// Adds a line to the display
	public void addLine(String line) {
		  try {
			  doc.insertAfterStart(el,"<tr>"+line +"</tr>");
		  }
		  catch (Exception e) {
			  System.out.println("Exception:" + e.getMessage());
		  }		
	}

	// Return a time stamp
	public String getTimeStamp() {
		Date now=new Date();
		DateFormat df=DateFormat.getTimeInstance();
		return df.format(now);
	}	
	

	
	// Handle an incoming DMR Frame
	void processFrame ()	{
	    maxref=max;
	    minref=min;
	    if (firstframe==true)	{
	    	
	    	audioDump();
	    	
	    	String l=getTimeStamp()+" Sync Acquired";
	    	l=l+" : center="+Integer.toString(center)+" jitter="+Integer.toString(jitter);
			addLine(l);
			fileWrite(l);
			return;
	    }
	    if ((synctype==11)||(synctype==12)) processDMRvoice ();
	     else processDMRdata ();
	    }

	// Handle a DMR Voice Frame
	void processDMRvoice ()	{	
		String l=getTimeStamp()+" DMR Voice Frame";
		if (frameSync==true) l=l+" (Sync)";
		l=dispSymbolsSinceLastFrame(l);
		addLine(l);
		fileWrite(l);
	}
	
	// Handle a DMR Data Frame
	void processDMRdata ()	{
		DMRDataDecode DMRdata=new DMRDataDecode();
		String line[]=new String[10];
		line=DMRdata.decode(getTimeStamp(),dibit_buf,inverted_dmr);
		if (frameSync==true) line[0]=line[0]+" (Sync)";
		line[0]=dispSymbolsSinceLastFrame(line[0]);
		displayLines(line);
	}

	// Display a group of lines
	void displayLines (String line[])	{
		int a;
		int len=line.length;
		for (a=(len-1);a>=0;a--)	{
			if (line[a]!=null) addLine(line[a]);
		}
		// Log to disk if needed
		if (logging==true)	{
			for (a=0;a<len;a++)	{
				if (line[a]!=null) fileWrite(line[a]);
			}
		}
	}
	
	// Write to a string to the logging file
	public boolean fileWrite(String fline) {
		// Add a CR to the end of each line
		fline=fline+"\r\n";
		// If we aren't logging don't try to do anything
		if (logging==false)
			return false;
		try {
			file.write(fline);
			file.flush();
		} catch (Exception e) {
			// Stop logging as we have a problem
			logging=false;
			System.out.println("\nError writing to the logging file");
			return false;
		}
		return true;
	}
	
	public String dispSymbolsSinceLastFrame (String l)	{
		l=l+" (Symbols="+Integer.toString(symbolcnt)+")";
		symbolcnt=0;
		return l;
	}
	
	// Grab 5 seconds worth of audio
	public void audioDump ()	{
		long a;
		final long sample_max=48000*5;
		int samples[]=new int[48000*5];
		for (a=0;a<sample_max;a++)	{
			samples[(int)a]=getAudio();
		}	
	    try	{
	    	FileWriter dfile=new FileWriter("audiodump.csv");
			for (a=0;a<sample_max;a++)	{
				dfile.write(Integer.toString(samples[(int)a]));
				dfile.write("\r\n");
			}
	    	dfile.flush();  
	    	dfile.close();
	    	}catch (Exception e)	{
	    		System.err.println("Error: " + e.getMessage());
	    		}
	    
	    System.exit(0);
		}
		
	
	
}
